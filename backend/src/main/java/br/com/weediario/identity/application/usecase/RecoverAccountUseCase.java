package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.AuthenticatedSession;
import br.com.weediario.identity.application.SessionIssuer;
import br.com.weediario.identity.application.exception.InvalidCredentialsException;
import br.com.weediario.identity.application.exception.LoginThrottledException;
import br.com.weediario.identity.application.port.LoginKeyHasher;
import br.com.weediario.identity.domain.model.LoginAttempts;
import br.com.weediario.identity.domain.model.PasswordHash;
import br.com.weediario.identity.domain.model.RecoveryVerifier;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.model.Username;
import br.com.weediario.identity.domain.repository.LoginAttemptsRepository;
import br.com.weediario.identity.domain.repository.RefreshTokenRepository;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.identity.domain.service.PasswordHasher;
import br.com.weediario.identity.domain.service.PasswordPolicy;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Recuperação de conta sem e-mail: apelido + derivado da frase de 12 palavras + senha nova.
 * <p>
 * Confere o derivado com o hash guardado no cadastro, troca a senha, encerra todas as sessões abertas (quem perdeu a
 * senha pode ter perdido o aparelho junto) e abre uma sessão nova. Tem limite de tentativas próprio por apelido e
 * leva o mesmo tempo exista a conta ou não.
 */
public final class RecoverAccountUseCase {

    /** Prefixo da chave de tentativas: falhas de recuperação não se misturam com as de login. */
    static final String ATTEMPT_PREFIX = "recover:";

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final SessionIssuer sessionIssuer;
    private final RefreshTokenRepository refreshTokens;
    private final LoginAttemptsRepository attempts;
    private final LoginKeyHasher keyHasher;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    private final PasswordHash timingDecoy;

    public RecoverAccountUseCase(UserRepository users, PasswordHasher passwordHasher, SessionIssuer sessionIssuer,
                                 RefreshTokenRepository refreshTokens, LoginAttemptsRepository attempts,
                                 LoginKeyHasher keyHasher, UnitOfWork unitOfWork, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.sessionIssuer = sessionIssuer;
        this.refreshTokens = refreshTokens;
        this.attempts = attempts;
        this.keyHasher = keyHasher;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
        this.timingDecoy = passwordHasher.hash("timing-decoy-" + System.nanoTime());
    }

    public AuthenticatedSession execute(Command command) {
        RecoveryVerifier verifier = new RecoveryVerifier(command.recoveryVerifier());
        PasswordPolicy.validate(command.newPassword());

        String attemptKey = keyHasher.keyOf(ATTEMPT_PREFIX + normalize(command.username()));
        Instant now = clock.instant();
        LoginAttempts current = unitOfWork.execute(() -> attempts.find(attemptKey)).orElse(LoginAttempts.none(now));
        current.lockRemaining(now).ifPresent(remaining -> {
            throw new LoginThrottledException(remaining);
        });

        Optional<User> candidate = findUser(command.username());
        // Conta sem frase (anterior à frase) compara com o decoy: mesmo tempo, sempre falha.
        boolean matches = passwordHasher.matches(verifier.value(),
                candidate.flatMap(User::recoveryHash).orElse(timingDecoy));
        Optional<User> recovered = candidate.filter(user -> matches && user.hasRecovery() && user.canAuthenticate());
        if (recovered.isEmpty()) {
            unitOfWork.run(() -> attempts.save(attemptKey, current.registerFailure(clock.instant())));
            throw new InvalidCredentialsException("Apelido ou frase de recuperação inválidos");
        }

        PasswordHash newPassword = passwordHasher.hash(command.newPassword());
        return unitOfWork.execute(() -> {
            attempts.delete(attemptKey);
            User user = recovered.get();
            user.changePassword(newPassword, clock);
            users.save(user);
            refreshTokens.revokeAllOfUser(user.id().value(), clock.instant());
            return sessionIssuer.startSession(user);
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

    private static String normalize(String rawUsername) {
        return rawUsername == null ? "" : rawUsername.strip().toLowerCase(Locale.ROOT);
    }

    /** @param recoveryVerifier derivado da frase, calculado no navegador */
    public record Command(String username, String recoveryVerifier, String newPassword) {

        @Override
        public String toString() {
            return "RecoverAccountUseCase.Command[***]";
        }
    }
}
