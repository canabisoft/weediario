package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.domain.model.RecoveryVerifier;
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

/**
 * Cadastro só com apelido e senha: nenhum e-mail ou dado civil. A frase de recuperação é gerada no navegador e só o
 * derivado dela chega aqui ({@link RecoveryVerifier}); guardamos o Argon2 desse derivado.
 */
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
        PasswordPolicy.validate(command.password());
        RecoveryVerifier verifier = new RecoveryVerifier(command.recoveryVerifier());
        TermsOfUse.requireCurrent(command.acceptedTermsVersion());

        // Hashes fora da transação: Argon2 é deliberadamente lento e não deve segurar conexão com o banco.
        var passwordHash = passwordHasher.hash(command.password());
        var recoveryHash = passwordHasher.hash(verifier.value());

        return unitOfWork.execute(() -> {
            if (users.existsByUsername(username)) {
                // O apelido é público de qualquer forma, então informar a indisponibilidade não vaza nada.
                throw new DomainException("Nome de usuário indisponível");
            }
            return users.save(User.register(username, passwordHash, recoveryHash, command.acceptedTermsVersion(),
                    clock)).id();
        });
    }

    /**
     * @param recoveryVerifier     derivado da frase de recuperação (as palavras ficam só com a pessoa)
     * @param acceptedTermsVersion versão dos Termos de Uso que a pessoa marcou como lida e aceita no cadastro
     */
    public record Command(String username, String password, String recoveryVerifier, String acceptedTermsVersion) {

        @Override
        public String toString() {
            return "RegisterUserUseCase.Command[username=" + username + "]";
        }
    }
}
