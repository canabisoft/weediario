package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.AuthenticatedSession;
import br.com.weediario.identity.application.LoginThrottle;
import br.com.weediario.identity.application.SessionIssuer;
import br.com.weediario.identity.application.exception.HashingBusyException;
import br.com.weediario.identity.application.exception.InvalidCredentialsException;
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
 * Login com apelido + senha.
 * <p>
 * Conta banida há menos de 30 dias entra, mas só para exportar os próprios dados (User#canSignInToExport; o
 * AccountStatusFilter recusa o resto da API).
 * <p>
 * Conta inexistente, senha errada e conta suspensa recebem a mesma resposta e levam o mesmo tempo (o Argon2 roda
 * contra um hash falso quando a conta não existe). A senha é escolhida por gente e pode ser adivinhada, então há limite
 * de tentativas por apelido ({@link LoginThrottle}).
 */
public final class AuthenticateUserUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final SessionIssuer sessionIssuer;
    private final LoginThrottle throttle;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    /** Comparado quando a conta não existe, para o tempo não revelar isso. */
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

    public AuthenticatedSession execute(Command command) {
        LoginThrottle.Attempt attempt = command.trustedDevice() == null
                ? throttle.begin(LoginThrottle.LOGIN, command.login())
                : throttle.beginOnDevice(command.trustedDevice(), command.login());
        Optional<User> user = findUser(command.login());
        String password = command.password() == null ? "" : command.password();
        boolean matches;
        try {
            matches = passwordHasher.matches(password, user.map(User::passwordHash).orElse(passwordDecoy));
        } catch (HashingBusyException busy) {
            throttle.released(attempt);
            throw busy;
        }

        Optional<User> authenticated = user.filter(found ->
                matches && found.canSignIn(clock.instant()));
        if (authenticated.isEmpty()) {
            throw new InvalidCredentialsException();
        }
        return unitOfWork.execute(() -> {
            throttle.succeeded(attempt);
            return sessionIssuer.startSession(authenticated.get());
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

    /**
     * @param login         apelido
     * @param trustedDevice aparelho que já entrou nesta conta (cookie conferido), ou {@code null}: muda só qual
     *                      contagem de tentativas vale ({@link LoginThrottle#beginOnDevice})
     */
    public record Command(String login, String password, String trustedDevice) {

        public Command(String login, String password) {
            this(login, password, null);
        }

        @Override
        public String toString() {
            return "AuthenticateUserUseCase.Command[***]";
        }
    }
}
