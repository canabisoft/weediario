package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.AuthenticatedSession;
import br.com.weediario.identity.application.SessionIssuer;
import br.com.weediario.identity.application.port.AccountMailer;
import br.com.weediario.identity.domain.model.AccountEmail;
import br.com.weediario.identity.domain.model.AccountToken;
import br.com.weediario.identity.domain.model.EmailAddress;
import br.com.weediario.identity.domain.model.RecoveryVerifier;
import br.com.weediario.identity.domain.model.TermsOfUse;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.model.Username;
import br.com.weediario.identity.domain.repository.AccountEmailRepository;
import br.com.weediario.identity.domain.repository.AccountTokenRepository;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.identity.domain.service.PasswordHasher;
import br.com.weediario.identity.domain.service.PasswordPolicy;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.CommerceFilter;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.util.Optional;

/**
 * Cadastro: apelido, senha, e-mail opcional e frase de recuperação. Nenhum dado civil.
 * <ul>
 *   <li>A frase é gerada no navegador e só o derivado dela chega aqui ({@link RecoveryVerifier}); guardamos o Argon2
 *   desse derivado. Toda conta tem frase, porque o e-mail é opcional.</li>
 *   <li>O e-mail, se informado, é guardado cifrado e só passa a valer para recuperar a senha depois de confirmado
 *   pelo link enviado a ele. Não é único e o cadastro nunca diz se um e-mail "já está em uso": num app de cannabis,
 *   saber que um endereço tem conta já seria um vazamento.</li>
 * </ul>
 * A conta já sai logada.
 */
public final class RegisterUserUseCase {

    private final UserRepository users;
    private final AccountEmailRepository emails;
    private final AccountTokenRepository tokens;
    private final PasswordHasher hasher;
    private final SessionIssuer sessionIssuer;
    private final AccountMailer mailer;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public RegisterUserUseCase(UserRepository users, AccountEmailRepository emails, AccountTokenRepository tokens,
                               PasswordHasher hasher, SessionIssuer sessionIssuer, AccountMailer mailer,
                               UnitOfWork unitOfWork, Clock clock) {
        this.users = users;
        this.emails = emails;
        this.tokens = tokens;
        this.hasher = hasher;
        this.sessionIssuer = sessionIssuer;
        this.mailer = mailer;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public AuthenticatedSession execute(Command command) {
        Username username = new Username(command.username());
        // O apelido aparece em todo comentário e diário: nada de telefone, PIX ou "zap" escondido nele.
        CommerceFilter.requireAllowed(username.value());
        String password = PasswordPolicy.requireAcceptable(command.password(), username);
        Optional<EmailAddress> email = command.email() == null || command.email().isBlank()
                ? Optional.empty()
                : Optional.of(new EmailAddress(command.email()));
        RecoveryVerifier verifier = new RecoveryVerifier(command.recoveryVerifier());
        TermsOfUse.requireCurrent(command.acceptedTermsVersion());

        // Argon2 fora da transação: é deliberadamente lento e não deve segurar conexão com o banco.
        var passwordHash = hasher.hash(password);
        var recoveryHash = hasher.hash(verifier.value());

        Registered registered = unitOfWork.execute(() -> {
            if (users.existsByUsername(username)) {
                // O apelido é público de qualquer forma, então informar a indisponibilidade não vaza nada.
                throw new DomainException("Nome de usuário indisponível");
            }
            User user = users.save(User.register(username, passwordHash, recoveryHash,
                    command.acceptedTermsVersion(), clock));
            Optional<String> verificationToken = email.map(address -> {
                emails.save(AccountEmail.unverified(user.id(), address));
                AccountToken.Issued issued = AccountToken.issue(user.id(), AccountToken.Purpose.VERIFY_EMAIL,
                        clock.instant());
                tokens.save(issued.token());
                return issued.rawValue();
            });
            return new Registered(sessionIssuer.startSession(user), verificationToken);
        });
        registered.verificationToken().ifPresent(token ->
                mailer.sendEmailVerification(email.get(), username.value(), token));
        return registered.session();
    }

    /**
     * @param email                opcional (nulo ou vazio = sem e-mail)
     * @param recoveryVerifier     derivado da frase de recuperação (as palavras ficam só com a pessoa)
     * @param acceptedTermsVersion versão dos Termos de Uso que a pessoa marcou como lida e aceita no cadastro
     */
    public record Command(String username, String password, String email, String recoveryVerifier,
                          String acceptedTermsVersion) {

        @Override
        public String toString() {
            return "RegisterUserUseCase.Command[username=" + username + "]";
        }
    }

    private record Registered(AuthenticatedSession session, Optional<String> verificationToken) {
    }
}
