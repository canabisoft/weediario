package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.AuthenticatedSession;
import br.com.weediario.identity.application.SessionIssuer;
import br.com.weediario.identity.application.exception.InvalidCredentialsException;
import br.com.weediario.identity.application.exception.LoginThrottledException;
import br.com.weediario.identity.application.port.LoginKeyHasher;
import br.com.weediario.identity.domain.model.Email;
import br.com.weediario.identity.domain.model.LoginAttempts;
import br.com.weediario.identity.domain.model.PasswordHash;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.repository.LoginAttemptsRepository;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.identity.domain.service.PasswordHasher;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

public final class AuthenticateUserUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final SessionIssuer sessionIssuer;
    private final LoginAttemptsRepository attempts;
    private final LoginKeyHasher keyHasher;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    /** Usado quando o e-mail não existe, para que a resposta leve o mesmo tempo (anti-enumeração por timing). */
    private final PasswordHash timingDecoy;

    public AuthenticateUserUseCase(UserRepository users, PasswordHasher passwordHasher, SessionIssuer sessionIssuer,
                                   LoginAttemptsRepository attempts, LoginKeyHasher keyHasher,
                                   UnitOfWork unitOfWork, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.sessionIssuer = sessionIssuer;
        this.attempts = attempts;
        this.keyHasher = keyHasher;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
        this.timingDecoy = passwordHasher.hash("timing-decoy-" + System.nanoTime());
    }

    public AuthenticatedSession execute(Command command) {
        String attemptKey = keyHasher.keyOf(normalize(command.email()));
        Instant now = clock.instant();
        LoginAttempts current = unitOfWork.execute(() -> attempts.find(attemptKey)).orElse(LoginAttempts.none(now));
        current.lockRemaining(now).ifPresent(remaining -> {
            throw new LoginThrottledException(remaining);
        });

        Optional<User> candidate = findUser(command.email());
        String password = command.password() == null ? "" : command.password();
        boolean passwordMatches = passwordHasher.matches(
                password, candidate.map(User::passwordHash).orElse(timingDecoy));

        Optional<User> authenticated = candidate.filter(found -> passwordMatches && found.canAuthenticate());
        if (authenticated.isEmpty()) {
            // Gravado antes de lançar a exceção: a falha precisa contar mesmo com a requisição terminando em erro.
            unitOfWork.run(() -> attempts.save(attemptKey, current.registerFailure(clock.instant())));
            throw new InvalidCredentialsException();
        }
        return unitOfWork.execute(() -> {
            attempts.delete(attemptKey);
            return sessionIssuer.startSession(authenticated.get());
        });
    }

    private Optional<User> findUser(String rawEmail) {
        try {
            return users.findByEmail(new Email(rawEmail));
        } catch (DomainException invalidFormat) {
            return Optional.empty();
        }
    }

    private static String normalize(String rawEmail) {
        return rawEmail == null ? "" : rawEmail.strip().toLowerCase(Locale.ROOT);
    }

    public record Command(String email, String password) {

        @Override
        public String toString() {
            return "AuthenticateUserUseCase.Command[***]";
        }
    }
}
