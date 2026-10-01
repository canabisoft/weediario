package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.exception.RegistrationRejectedException;
import br.com.weediario.identity.domain.model.Email;
import br.com.weediario.identity.domain.model.TermsOfUse;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.model.UserId;
import br.com.weediario.identity.domain.model.Username;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.identity.domain.service.PasswordHasher;
import br.com.weediario.identity.domain.service.PasswordPolicy;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;

public final class RegisterUserUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public RegisterUserUseCase(UserRepository users, PasswordHasher passwordHasher, UnitOfWork unitOfWork, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public UserId execute(Command command) {
        Username username = new Username(command.username());
        Email email = new Email(command.email());
        PasswordPolicy.validate(command.password());
        TermsOfUse.requireCurrent(command.acceptedTermsVersion());

        // Hash fora da transação: Argon2 é deliberadamente lento e não deve segurar conexão com o banco.
        var passwordHash = passwordHasher.hash(command.password());

        return unitOfWork.execute(() -> {
            if (users.existsByUsername(username)) {
                // O apelido é público de qualquer forma, então informar a indisponibilidade não vaza nada.
                throw new DomainException("Nome de usuário indisponível");
            }
            if (users.existsByEmail(email)) {
                throw new RegistrationRejectedException();
            }
            return users.save(User.register(username, email, passwordHash, command.acceptedTermsVersion(), clock)).id();
        });
    }

    /** @param acceptedTermsVersion versão dos Termos de Uso que a pessoa marcou como lida e aceita no cadastro */
    public record Command(String username, String email, String password, String acceptedTermsVersion) {

        @Override
        public String toString() {
            return "RegisterUserUseCase.Command[username=" + username + "]";
        }
    }
}
