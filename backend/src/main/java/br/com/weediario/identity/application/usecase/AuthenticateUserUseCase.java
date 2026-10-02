package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.AuthenticatedSession;
import br.com.weediario.identity.application.LoginThrottle;
import br.com.weediario.identity.application.SecurityNotifier;
import br.com.weediario.identity.application.SessionIssuer;
import br.com.weediario.identity.application.exception.InvalidCredentialsException;
import br.com.weediario.identity.domain.model.AccessKey;
import br.com.weediario.identity.domain.model.PasswordHash;
import br.com.weediario.identity.domain.model.SecurityEvent;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.model.Username;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.identity.domain.service.PasswordHasher;
import br.com.weediario.identity.domain.service.PasswordPolicy;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.util.Optional;

/**
 * Login com apelido + senha. Também cobre a transição das contas da época da chave de acesso: um último login com a
 * chave, que cria a senha e invalida a chave.
 * <p>
 * Conta inexistente, senha errada e conta suspensa recebem a mesma resposta e levam o mesmo tempo (o Argon2 roda
 * contra um hash falso quando a conta não existe). A senha é escolhida por gente e pode ser adivinhada, então há limite
 * de tentativas por apelido ({@link LoginThrottle}); a chave de acesso tem 256 bits e não precisa.
 */
public final class AuthenticateUserUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final SessionIssuer sessionIssuer;
    private final LoginThrottle throttle;
    private final SecurityNotifier security;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    /** Comparado quando a conta não existe (ou ainda não tem senha), para o tempo não revelar isso. */
    private final PasswordHash passwordDecoy;

    public AuthenticateUserUseCase(UserRepository users, PasswordHasher passwordHasher, SessionIssuer sessionIssuer,
                                   LoginThrottle throttle, SecurityNotifier security, UnitOfWork unitOfWork,
                                   Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.sessionIssuer = sessionIssuer;
        this.throttle = throttle;
        this.security = security;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
        this.passwordDecoy = passwordHasher.hash("timing-decoy-" + System.nanoTime());
    }

    public AuthenticatedSession execute(Command command) {
        LoginThrottle.Attempt attempt = throttle.begin(LoginThrottle.LOGIN, command.login());
        Optional<User> user = findUser(command.login());
        String password = command.password() == null ? "" : command.password();
        boolean matches = passwordHasher.matches(password, user.flatMap(User::passwordHash).orElse(passwordDecoy));

        Optional<User> authenticated = user.filter(found ->
                matches && found.hasPassword() && found.canAuthenticate());
        if (authenticated.isEmpty()) {
            throttle.failed(attempt);
            throw new InvalidCredentialsException();
        }
        return unitOfWork.execute(() -> {
            throttle.succeeded(attempt);
            return sessionIssuer.startSession(authenticated.get());
        });
    }

    /**
     * Conta da época da chave de acesso: entra com a chave uma última vez e cria a senha. Dá certo uma vez só; depois
     * disso a chave deixa de valer e a conta entra com a senha.
     */
    public AuthenticatedSession loginWithAccessKey(AccessKeyCommand command) {
        Optional<User> migrating = findUser(command.login()).filter(found ->
                AccessKey.isWellFormed(command.accessKey())
                        && found.accessKeyMatches(new AccessKey(command.accessKey()).hash())
                        && found.canAuthenticate());
        if (migrating.isEmpty()) {
            throw new InvalidCredentialsException("Apelido ou chave de acesso inválidos");
        }
        User user = migrating.get();
        var newHash = passwordHasher.hash(PasswordPolicy.requireAcceptable(command.newPassword(), user.username()));
        AuthenticatedSession session = unitOfWork.execute(() -> {
            User fresh = users.findById(user.id()).orElseThrow(InvalidCredentialsException::new);
            fresh.changePassword(newHash, clock);
            users.save(fresh);
            return sessionIssuer.startSession(fresh);
        });
        security.recorded(user, SecurityEvent.Kind.PASSWORD_CREATED_FROM_ACCESS_KEY);
        return session;
    }

    /** Formato inválido de apelido conta como conta inexistente. */
    private Optional<User> findUser(String rawLogin) {
        if (rawLogin == null) {
            return Optional.empty();
        }
        try {
            return users.findByUsername(new Username(rawLogin.strip()).value());
        } catch (DomainException invalidFormat) {
            return Optional.empty();
        }
    }

    /** @param login apelido */
    public record Command(String login, String password) {

        @Override
        public String toString() {
            return "AuthenticateUserUseCase.Command[***]";
        }
    }

    public record AccessKeyCommand(String login, String accessKey, String newPassword) {

        @Override
        public String toString() {
            return "AuthenticateUserUseCase.AccessKeyCommand[***]";
        }
    }
}
