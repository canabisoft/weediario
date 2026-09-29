package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.AuthenticatedSession;
import br.com.weediario.identity.application.SessionIssuer;
import br.com.weediario.identity.application.exception.InvalidCredentialsException;
import br.com.weediario.identity.domain.model.Email;
import br.com.weediario.identity.domain.model.PasswordHash;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.identity.domain.service.PasswordHasher;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.util.Optional;

public final class AuthenticateUserUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final SessionIssuer sessionIssuer;
    private final UnitOfWork unitOfWork;
    /** Usado quando o e-mail não existe, para que a resposta leve o mesmo tempo (anti-enumeração por timing). */
    private final PasswordHash timingDecoy;

    public AuthenticateUserUseCase(UserRepository users, PasswordHasher passwordHasher,
                                   SessionIssuer sessionIssuer, UnitOfWork unitOfWork) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.sessionIssuer = sessionIssuer;
        this.unitOfWork = unitOfWork;
        this.timingDecoy = passwordHasher.hash("timing-decoy-" + System.nanoTime());
    }

    public AuthenticatedSession execute(Command command) {
        Optional<User> candidate = findUser(command.email());
        String password = command.password() == null ? "" : command.password();

        boolean passwordMatches = passwordHasher.matches(
                password, candidate.map(User::passwordHash).orElse(timingDecoy));

        User user = candidate
                .filter(found -> passwordMatches && found.canAuthenticate())
                .orElseThrow(InvalidCredentialsException::new);

        return unitOfWork.execute(() -> sessionIssuer.startSession(user));
    }

    private Optional<User> findUser(String rawEmail) {
        try {
            return users.findByEmail(new Email(rawEmail));
        } catch (DomainException invalidFormat) {
            return Optional.empty();
        }
    }

    public record Command(String email, String password) {

        @Override
        public String toString() {
            return "AuthenticateUserUseCase.Command[***]";
        }
    }
}
