package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.AuthenticatedSession;
import br.com.weediario.identity.application.SecurityNotifier;
import br.com.weediario.identity.application.SessionIssuer;
import br.com.weediario.identity.application.exception.InvalidCredentialsException;
import br.com.weediario.identity.domain.model.AccountToken;
import br.com.weediario.identity.domain.model.PasswordHash;
import br.com.weediario.identity.domain.model.RecoveryVerifier;
import br.com.weediario.identity.domain.model.SecurityEvent;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.model.Username;
import br.com.weediario.identity.domain.repository.AccountTokenRepository;
import br.com.weediario.identity.domain.repository.RefreshTokenRepository;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.identity.domain.service.PasswordHasher;
import br.com.weediario.identity.domain.service.PasswordPolicy;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.util.Optional;

/**
 * Recuperação de conta pela frase de 12 palavras: apelido + derivado da frase + senha nova + frase nova.
 * <p>
 * Confere o derivado com o hash guardado, define a senha nova, <b>troca a frase</b> (a antiga acabou de ser digitada e
 * pode ter sido vista), invalida links de redefinição pendentes, encerra todas as sessões abertas (quem esqueceu a
 * senha pode ter perdido o aparelho junto) e abre uma sessão nova. Avisa o e-mail confirmado, se houver.
 * <p>
 * Leva o mesmo tempo exista a conta ou não. Sem bloqueio por apelido: a frase tem 128 bits (adivinhar é inviável), e
 * bloquear só deixaria alguém impedir o dono de recuperar a própria conta. A sobrecarga é contida pelo teto de Argon2.
 */
public final class RecoverAccountUseCase {

    private final UserRepository users;
    private final AccountTokenRepository tokens;
    private final PasswordHasher hasher;
    private final SessionIssuer sessionIssuer;
    private final RefreshTokenRepository refreshTokens;
    private final SecurityNotifier security;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    private final PasswordHash timingDecoy;

    public RecoverAccountUseCase(UserRepository users, AccountTokenRepository tokens, PasswordHasher hasher,
                                 SessionIssuer sessionIssuer, RefreshTokenRepository refreshTokens,
                                 SecurityNotifier security, UnitOfWork unitOfWork, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.hasher = hasher;
        this.sessionIssuer = sessionIssuer;
        this.refreshTokens = refreshTokens;
        this.security = security;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
        this.timingDecoy = hasher.hash("timing-decoy-" + System.nanoTime());
    }

    public AuthenticatedSession execute(Command command) {
        RecoveryVerifier verifier = new RecoveryVerifier(command.recoveryVerifier());
        RecoveryVerifier newVerifier = new RecoveryVerifier(command.newRecoveryVerifier());
        if (newVerifier.equals(verifier)) {
            throw new DomainException("Gere uma frase nova: a antiga acabou de ser digitada e deixa de valer");
        }

        Optional<User> candidate = findUser(command.username());
        // Conta sem frase (anterior à frase) compara com o decoy: mesmo tempo, sempre falha.
        boolean matches = hasher.matches(verifier.value(), candidate.flatMap(User::recoveryHash).orElse(timingDecoy));
        Optional<User> recovered = candidate.filter(user -> matches && user.hasRecovery() && user.canAuthenticate());
        if (recovered.isEmpty()) {
            throw new InvalidCredentialsException("Apelido ou frase de recuperação inválidos");
        }
        User user = recovered.get();
        // A senha nova só é validada depois de a frase conferir: a mensagem de erro não pode ajudar a testar frases.
        PasswordHash passwordHash = hasher.hash(PasswordPolicy.requireAcceptable(command.newPassword(), user.username()));
        PasswordHash recoveryHash = hasher.hash(newVerifier.value());

        AuthenticatedSession session = unitOfWork.execute(() -> {
            User fresh = users.findById(user.id()).orElseThrow(InvalidCredentialsException::new);
            fresh.changePassword(passwordHash, clock);
            fresh.changeRecovery(recoveryHash, clock);
            users.save(fresh);
            tokens.deletePending(fresh.id(), AccountToken.Purpose.RESET_PASSWORD);
            refreshTokens.revokeAllOfUser(fresh.id().value(), clock.instant());
            return sessionIssuer.startSession(fresh);
        });
        security.recorded(user, SecurityEvent.Kind.PASSWORD_RESET_BY_PHRASE);
        return session;
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

    /**
     * @param recoveryVerifier    derivado da frase atual, calculado no navegador
     * @param newRecoveryVerifier derivado da frase nova, gerada no navegador durante a recuperação
     */
    public record Command(String username, String recoveryVerifier, String newPassword, String newRecoveryVerifier) {

        @Override
        public String toString() {
            return "RecoverAccountUseCase.Command[***]";
        }
    }
}
