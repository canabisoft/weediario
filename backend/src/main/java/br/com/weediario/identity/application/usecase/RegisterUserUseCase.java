package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.KeyedSession;
import br.com.weediario.identity.application.SessionIssuer;
import br.com.weediario.identity.domain.model.AccessKey;
import br.com.weediario.identity.domain.model.RecoveryVerifier;
import br.com.weediario.identity.domain.model.TermsOfUse;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.model.Username;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.identity.domain.service.PasswordHasher;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.CommerceFilter;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;

/**
 * Cadastro só com apelido: nenhum e-mail, senha ou dado civil. O servidor gera a {@link AccessKey chave de acesso},
 * devolve uma única vez e guarda só o hash dela. A frase de recuperação é gerada no navegador e só o derivado dela
 * chega aqui ({@link RecoveryVerifier}); guardamos o Argon2 desse derivado. A conta já sai logada.
 */
public final class RegisterUserUseCase {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final SessionIssuer sessionIssuer;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public RegisterUserUseCase(UserRepository users, PasswordHasher hasher, SessionIssuer sessionIssuer,
                               UnitOfWork unitOfWork, Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.sessionIssuer = sessionIssuer;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public KeyedSession execute(Command command) {
        Username username = new Username(command.username());
        // O apelido aparece em todo comentário e diário: nada de telefone, PIX ou "zap" escondido nele.
        CommerceFilter.requireAllowed(username.value());
        RecoveryVerifier verifier = new RecoveryVerifier(command.recoveryVerifier());
        TermsOfUse.requireCurrent(command.acceptedTermsVersion());

        // Argon2 fora da transação: é deliberadamente lento e não deve segurar conexão com o banco.
        var recoveryHash = hasher.hash(verifier.value());
        AccessKey accessKey = AccessKey.generate();

        return unitOfWork.execute(() -> {
            if (users.existsByUsername(username)) {
                // O apelido é público de qualquer forma, então informar a indisponibilidade não vaza nada.
                throw new DomainException("Nome de usuário indisponível");
            }
            User user = users.save(User.register(username, accessKey.hash(), recoveryHash,
                    command.acceptedTermsVersion(), clock));
            return new KeyedSession(sessionIssuer.startSession(user), accessKey);
        });
    }

    /**
     * @param recoveryVerifier     derivado da frase de recuperação (as palavras ficam só com a pessoa)
     * @param acceptedTermsVersion versão dos Termos de Uso que a pessoa marcou como lida e aceita no cadastro
     */
    public record Command(String username, String recoveryVerifier, String acceptedTermsVersion) {

        @Override
        public String toString() {
            return "RegisterUserUseCase.Command[username=" + username + "]";
        }
    }
}
