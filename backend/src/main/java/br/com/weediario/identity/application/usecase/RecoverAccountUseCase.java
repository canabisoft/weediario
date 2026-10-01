package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.KeyedSession;
import br.com.weediario.identity.application.LoginThrottle;
import br.com.weediario.identity.application.SessionIssuer;
import br.com.weediario.identity.application.exception.InvalidCredentialsException;
import br.com.weediario.identity.domain.model.AccessKey;
import br.com.weediario.identity.domain.model.PasswordHash;
import br.com.weediario.identity.domain.model.RecoveryVerifier;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.model.Username;
import br.com.weediario.identity.domain.repository.RefreshTokenRepository;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.identity.domain.service.PasswordHasher;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.util.Optional;

/**
 * Recuperação de conta: apelido + derivado da frase de 12 palavras.
 * <p>
 * Confere o derivado com o hash guardado, gera uma chave de acesso nova (a antiga deixa de valer), encerra todas as
 * sessões abertas (quem perdeu a chave pode ter perdido o aparelho junto) e abre uma sessão nova. Tem limite de
 * tentativas próprio por apelido e leva o mesmo tempo exista a conta ou não.
 */
public final class RecoverAccountUseCase {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final SessionIssuer sessionIssuer;
    private final RefreshTokenRepository refreshTokens;
    private final LoginThrottle throttle;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    private final PasswordHash timingDecoy;

    public RecoverAccountUseCase(UserRepository users, PasswordHasher hasher, SessionIssuer sessionIssuer,
                                 RefreshTokenRepository refreshTokens, LoginThrottle throttle,
                                 UnitOfWork unitOfWork, Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.sessionIssuer = sessionIssuer;
        this.refreshTokens = refreshTokens;
        this.throttle = throttle;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
        this.timingDecoy = hasher.hash("timing-decoy-" + System.nanoTime());
    }

    public KeyedSession execute(Command command) {
        RecoveryVerifier verifier = new RecoveryVerifier(command.recoveryVerifier());
        LoginThrottle.Attempt attempt = throttle.begin(LoginThrottle.RECOVERY, command.username());

        Optional<User> candidate = findUser(command.username());
        // Conta sem frase (anterior à frase) compara com o decoy: mesmo tempo, sempre falha.
        boolean matches = hasher.matches(verifier.value(), candidate.flatMap(User::recoveryHash).orElse(timingDecoy));
        Optional<User> recovered = candidate.filter(user -> matches && user.hasRecovery() && user.canAuthenticate());
        if (recovered.isEmpty()) {
            throttle.failed(attempt);
            throw new InvalidCredentialsException("Apelido ou frase de recuperação inválidos");
        }

        AccessKey accessKey = AccessKey.generate();
        return unitOfWork.execute(() -> {
            throttle.succeeded(attempt);
            User user = recovered.get();
            user.replaceAccessKey(accessKey.hash(), clock);
            users.save(user);
            refreshTokens.revokeAllOfUser(user.id().value(), clock.instant());
            return new KeyedSession(sessionIssuer.startSession(user), accessKey);
        });
    }

    private Optional<User> findUser(String rawUsername) {
        if (rawUsername == null) {
            return Optional.empty();
        }
        try {
            return users.findByUsername(new Username(rawUsername.strip()).value());
        } catch (DomainException invalidFormat) {
            return Optional.empty();
        }
    }

    /** @param recoveryVerifier derivado da frase, calculado no navegador */
    public record Command(String username, String recoveryVerifier) {

        @Override
        public String toString() {
            return "RecoverAccountUseCase.Command[***]";
        }
    }
}
