package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.AuthenticatedSession;
import br.com.weediario.identity.application.KeyedSession;
import br.com.weediario.identity.application.LoginThrottle;
import br.com.weediario.identity.application.SessionIssuer;
import br.com.weediario.identity.application.exception.InvalidCredentialsException;
import br.com.weediario.identity.domain.model.AccessKey;
import br.com.weediario.identity.domain.model.AccessKeyHash;
import br.com.weediario.identity.domain.model.PasswordHash;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.model.Username;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.identity.domain.service.PasswordHasher;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.util.Optional;

/**
 * Login com apelido + chave de acesso. Também cobre a transição de contas antigas: um último login com a senha, que
 * troca a senha por uma chave de acesso e apaga a senha.
 * <p>
 * Conta inexistente, chave errada e conta suspensa recebem a mesma resposta. Só o login com senha (conta antiga) tem
 * limite de tentativas por apelido: senha escolhida por gente pode ser adivinhada, chave de 256 bits não.
 */
public final class AuthenticateUserUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final SessionIssuer sessionIssuer;
    private final LoginThrottle throttle;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    /**
     * Comparado no login de conta antiga quando não há senha para conferir: Argon2 é lento e, sem o decoy, o tempo
     * revelaria se a conta existe. (A chave de acesso usa SHA-256, rápido demais para essa diferença aparecer.)
     */
    private final PasswordHash passwordDecoy;

    public AuthenticateUserUseCase(UserRepository users, PasswordHasher passwordHasher, SessionIssuer sessionIssuer,
                                   LoginThrottle throttle, UnitOfWork unitOfWork, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.sessionIssuer = sessionIssuer;
        this.throttle = throttle;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
        this.passwordDecoy = passwordHasher.hash("timing-decoy-" + System.nanoTime());
    }

    /**
     * Sem limite de tentativas: a chave tem 256 bits (adivinhar é inviável) e um bloqueio por apelido só serviria
     * para alguém trancar a conta dos outros. A sobrecarga é contida pelo teto de cálculos pesados.
     */
    public AuthenticatedSession execute(Command command) {
        // Chave mal formada nem é comparada: conta como errada.
        Optional<AccessKeyHash> candidate = AccessKey.isWellFormed(command.accessKey())
                ? Optional.of(new AccessKey(command.accessKey()).hash())
                : Optional.empty();
        Optional<User> authenticated = findUser(command.login())
                .filter(found -> candidate.map(found::accessKeyMatches).orElse(false) && found.canAuthenticate());
        if (authenticated.isEmpty()) {
            throw new InvalidCredentialsException();
        }
        return unitOfWork.execute(() -> sessionIssuer.startSession(authenticated.get()));
    }

    /**
     * Último login com senha de uma conta anterior à chave de acesso. Dá certo uma vez só: a conta recebe a chave e a
     * senha é apagada.
     */
    public KeyedSession migrateLegacy(LegacyCommand command) {
        LoginThrottle.Attempt attempt = throttle.begin(LoginThrottle.LOGIN, command.login());
        Optional<User> user = findUser(command.login());
        String password = command.password() == null ? "" : command.password();
        boolean matches = passwordHasher.matches(password,
                user.flatMap(User::legacyPasswordHash).orElse(passwordDecoy));

        Optional<User> migrating = user.filter(found ->
                matches && found.legacyPasswordHash().isPresent() && found.canAuthenticate());
        if (migrating.isEmpty()) {
            throttle.failed(attempt);
            throw new InvalidCredentialsException();
        }
        AccessKey accessKey = AccessKey.generate();
        return unitOfWork.execute(() -> {
            throttle.succeeded(attempt);
            User found = migrating.get();
            found.replaceAccessKey(accessKey.hash(), clock);
            users.save(found);
            return new KeyedSession(sessionIssuer.startSession(found), accessKey);
        });
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
    public record Command(String login, String accessKey) {

        @Override
        public String toString() {
            return "AuthenticateUserUseCase.Command[***]";
        }
    }

    public record LegacyCommand(String login, String password) {

        @Override
        public String toString() {
            return "AuthenticateUserUseCase.LegacyCommand[***]";
        }
    }
}
