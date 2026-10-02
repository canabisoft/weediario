package br.com.weediario.identity.application;

import br.com.weediario.identity.application.exception.LoginThrottledException;
import br.com.weediario.identity.application.port.LoginKeyHasher;
import br.com.weediario.identity.domain.model.LoginAttempts;
import br.com.weediario.identity.domain.repository.LoginAttemptsRepository;
import br.com.weediario.shared.application.UnitOfWork;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

/**
 * Limite de tentativas por apelido (nunca por IP) para tudo que confere a senha: login e a confirmação da senha atual
 * antes de mudanças sensíveis. Senha escolhida por gente pode ser adivinhada. Frase de recuperação, chave de acesso
 * antiga e links de e-mail não têm limite (são aleatórios e longos demais para adivinhar, e um bloqueio por apelido só
 * deixaria alguém impedir o dono de recuperar a conta). A chave guardada é um HMAC do apelido normalizado.
 */
public final class LoginThrottle {

    /** Login com senha e confirmação da senha atual dividem o mesmo limite. */
    public static final String LOGIN = "";

    private final LoginAttemptsRepository attempts;
    private final LoginKeyHasher keyHasher;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public LoginThrottle(LoginAttemptsRepository attempts, LoginKeyHasher keyHasher, UnitOfWork unitOfWork, Clock clock) {
        this.attempts = attempts;
        this.keyHasher = keyHasher;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    /** Lança {@link LoginThrottledException} se o apelido está bloqueado. */
    public Attempt begin(String scope, String rawUsername) {
        String key = keyHasher.keyOf(scope + (rawUsername == null ? "" : rawUsername.strip().toLowerCase(Locale.ROOT)));
        Instant now = clock.instant();
        LoginAttempts current = unitOfWork.execute(() -> attempts.find(key)).orElse(LoginAttempts.none(now));
        current.lockRemaining(now).ifPresent(remaining -> {
            throw new LoginThrottledException(remaining);
        });
        return new Attempt(key, current);
    }

    /** Gravado em transação própria: a falha precisa contar mesmo com a requisição terminando em erro. */
    public void failed(Attempt attempt) {
        unitOfWork.run(() -> attempts.save(attempt.key(), attempt.current().registerFailure(clock.instant())));
    }

    /** Chamado dentro da transação do sucesso. */
    public void succeeded(Attempt attempt) {
        attempts.delete(attempt.key());
    }

    public record Attempt(String key, LoginAttempts current) {
    }
}
