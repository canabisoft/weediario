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

    /**
     * Reserva uma tentativa antes de conferir a senha: ela já conta como falha e só é desfeita por
     * {@link #succeeded}. A leitura trava a linha do apelido, então tentativas simultâneas entram em fila e cada uma
     * conta; quando o limite é atingido, as seguintes recebem {@link LoginThrottledException} sem nem rodar o Argon2.
     */
    public Attempt begin(String scope, String rawUsername) {
        String key = keyHasher.keyOf(scope + (rawUsername == null ? "" : rawUsername.strip().toLowerCase(Locale.ROOT)));
        Instant now = clock.instant();
        unitOfWork.run(() -> {
            LoginAttempts current = attempts.findForUpdate(key, now);
            current.lockRemaining(now).ifPresent(remaining -> {
                throw new LoginThrottledException(remaining);
            });
            attempts.save(key, current.registerFailure(now));
        });
        return new Attempt(key);
    }

    /** A senha nem foi conferida (teto de Argon2 simultâneos): a tentativa não conta. */
    public void released(Attempt attempt) {
        unitOfWork.run(() -> {
            LoginAttempts refunded = attempts.findForUpdate(attempt.key(), clock.instant()).refund();
            if (refunded.isClean()) {
                attempts.delete(attempt.key());
            } else {
                attempts.save(attempt.key(), refunded);
            }
        });
    }

    /** Senha certa: zera a contagem do apelido. Chamado dentro da transação do sucesso. */
    public void succeeded(Attempt attempt) {
        attempts.delete(attempt.key());
    }

    public record Attempt(String key) {
    }
}
