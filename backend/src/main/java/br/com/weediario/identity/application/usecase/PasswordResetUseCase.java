package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.AuthenticatedSession;
import br.com.weediario.identity.application.SecurityNotifier;
import br.com.weediario.identity.application.SessionIssuer;
import br.com.weediario.identity.application.exception.InvalidCredentialsException;
import br.com.weediario.identity.application.port.AccountMailer;
import br.com.weediario.identity.application.port.BackgroundTasks;
import br.com.weediario.identity.domain.model.AccountEmail;
import br.com.weediario.identity.domain.model.AccountToken;
import br.com.weediario.identity.domain.model.SecurityEvent;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.model.Username;
import br.com.weediario.identity.domain.repository.AccountEmailRepository;
import br.com.weediario.identity.domain.repository.AccountTokenRepository;
import br.com.weediario.identity.domain.repository.RefreshTokenRepository;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.identity.domain.service.PasswordHasher;
import br.com.weediario.identity.domain.service.PasswordPolicy;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Redefinição de senha pelo e-mail confirmado da conta.
 * <ul>
 *   <li>O pedido parte do apelido (público), nunca do e-mail: não existe busca por endereço.</li>
 *   <li>A resposta é sempre a mesma, tenha a conta e-mail ou não. O pedido inteiro (procurar a conta, gravar o
 *   token, mandar o e-mail) roda em segundo plano, então o tempo da resposta também não muda.</li>
 *   <li>No máximo um link a cada {@link #RESEND_INTERVAL} por conta, para ninguém lotar a caixa de entrada de outra
 *   pessoa pedindo redefinições.</li>
 *   <li>Usar o link troca a senha, invalida os outros links e encerra todas as sessões.</li>
 * </ul>
 */
public final class PasswordResetUseCase {

    static final Duration RESEND_INTERVAL = Duration.ofMinutes(2);

    private final UserRepository users;
    private final AccountEmailRepository emails;
    private final AccountTokenRepository tokens;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordHasher hasher;
    private final SessionIssuer sessionIssuer;
    private final AccountMailer mailer;
    private final BackgroundTasks background;
    private final SecurityNotifier security;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public PasswordResetUseCase(UserRepository users, AccountEmailRepository emails, AccountTokenRepository tokens,
                                RefreshTokenRepository refreshTokens, PasswordHasher hasher,
                                SessionIssuer sessionIssuer, AccountMailer mailer, BackgroundTasks background,
                                SecurityNotifier security, UnitOfWork unitOfWork, Clock clock) {
        this.users = users;
        this.emails = emails;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.hasher = hasher;
        this.sessionIssuer = sessionIssuer;
        this.mailer = mailer;
        this.background = background;
        this.security = security;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    /** Nunca falha nem diz se mandou: "se a conta tiver e-mail confirmado, enviamos um link". */
    public void request(String rawUsername) {
        background.submit(() -> process(rawUsername));
    }

    private void process(String rawUsername) {
        Optional<Pending> pending = unitOfWork.execute(() -> findUser(rawUsername)
                .filter(User::canAuthenticate)
                .flatMap(user -> emails.find(user.id()).filter(AccountEmail::isVerified)
                        .filter(email -> canResend(user))
                        .map(email -> {
                            AccountToken.Issued issued = AccountToken.issue(user.id(),
                                    AccountToken.Purpose.RESET_PASSWORD, clock.instant());
                            tokens.save(issued.token());
                            return new Pending(email, user.username().value(), issued.rawValue());
                        })));
        pending.ifPresent(found -> mailer.sendPasswordReset(found.email().address(), found.username(), found.token()));
    }

    public AuthenticatedSession confirm(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidCredentialsException("Link inválido ou expirado");
        }
        // Validado antes do Argon2 (lento) e fora da transação, que não deve segurar a conexão com o banco.
        AccountToken peek = unitOfWork.execute(() -> tokens.findByHash(AccountToken.hashOf(rawToken.strip())))
                .filter(token -> token.isUsable(AccountToken.Purpose.RESET_PASSWORD, clock.instant()))
                .orElseThrow(() -> new InvalidCredentialsException("Link inválido ou expirado"));
        User owner = unitOfWork.execute(() -> users.findById(peek.userId()))
                .filter(User::canAuthenticate)
                .orElseThrow(() -> new InvalidCredentialsException("Link inválido ou expirado"));
        var newHash = hasher.hash(PasswordPolicy.requireAcceptable(newPassword, owner.username()));

        AuthenticatedSession session = unitOfWork.execute(() -> {
            Instant now = clock.instant();
            // Relido com lock: dois cliques no mesmo link não trocam a senha duas vezes.
            AccountToken token = tokens.findByHash(peek.tokenHash())
                    .filter(found -> found.isUsable(AccountToken.Purpose.RESET_PASSWORD, now))
                    .orElseThrow(() -> new InvalidCredentialsException("Link inválido ou expirado"));
            token.markUsed(now);
            tokens.save(token);
            tokens.deletePending(owner.id(), AccountToken.Purpose.RESET_PASSWORD);
            User user = users.findById(owner.id()).orElseThrow();
            user.changePassword(newHash, clock);
            users.save(user);
            refreshTokens.revokeAllOfUser(user.id().value(), now);
            return sessionIssuer.startSession(user);
        });
        security.recorded(owner, SecurityEvent.Kind.PASSWORD_RESET_BY_EMAIL);
        return session;
    }

    private boolean canResend(User user) {
        return tokens.lastIssuedAt(user.id(), AccountToken.Purpose.RESET_PASSWORD)
                .map(last -> !clock.instant().isBefore(last.plus(RESEND_INTERVAL)))
                .orElse(true);
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

    private record Pending(AccountEmail email, String username, String token) {
    }
}
