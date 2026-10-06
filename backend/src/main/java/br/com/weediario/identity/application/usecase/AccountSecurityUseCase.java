package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.AuthenticatedSession;
import br.com.weediario.identity.application.CredentialCheck;
import br.com.weediario.identity.application.SecurityNotifier;
import br.com.weediario.identity.application.SessionIssuer;
import br.com.weediario.identity.application.exception.InvalidCredentialsException;
import br.com.weediario.identity.application.port.AccountMailer;
import br.com.weediario.identity.domain.model.AccountEmail;
import br.com.weediario.identity.domain.model.AccountToken;
import br.com.weediario.identity.domain.model.EmailAddress;
import br.com.weediario.identity.domain.model.SecurityEvent;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.model.UserId;
import br.com.weediario.identity.domain.repository.AccountEmailRepository;
import br.com.weediario.identity.domain.repository.AccountTokenRepository;
import br.com.weediario.identity.domain.repository.RefreshTokenRepository;
import br.com.weediario.identity.domain.repository.SecurityEventRepository;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.identity.domain.service.PasswordHasher;
import br.com.weediario.identity.domain.service.PasswordPolicy;
import br.com.weediario.shared.application.ActionLimits;
import br.com.weediario.shared.application.ActionQuota;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Configurações de segurança da própria conta: senha, e-mail opcional (e a confirmação dele) e o histórico de
 * mudanças sensíveis. Toda mudança exige a senha atual, mesmo com a sessão aberta (ver {@link CredentialCheck}).
 */
public final class AccountSecurityUseCase {

    static final Duration RESEND_INTERVAL = Duration.ofMinutes(2);

    private final UserRepository users;
    private final AccountEmailRepository emails;
    private final AccountTokenRepository tokens;
    private final SecurityEventRepository events;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordHasher hasher;
    private final CredentialCheck credentials;
    private final SessionIssuer sessionIssuer;
    private final AccountMailer mailer;
    private final SecurityNotifier security;
    private final ActionQuota quota;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public AccountSecurityUseCase(UserRepository users, AccountEmailRepository emails, AccountTokenRepository tokens,
                                  SecurityEventRepository events, RefreshTokenRepository refreshTokens,
                                  PasswordHasher hasher, CredentialCheck credentials, SessionIssuer sessionIssuer,
                                  AccountMailer mailer, SecurityNotifier security,
                                  ActionQuota quota, UnitOfWork unitOfWork,
                                  Clock clock) {
        this.users = users;
        this.emails = emails;
        this.tokens = tokens;
        this.events = events;
        this.refreshTokens = refreshTokens;
        this.hasher = hasher;
        this.credentials = credentials;
        this.sessionIssuer = sessionIssuer;
        this.mailer = mailer;
        this.security = security;
        this.quota = quota;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public Overview overview(UUID userId) {
        return unitOfWork.execute(() -> {
            User user = load(userId);
            Optional<AccountEmail> email = emails.find(user.id());
            List<Event> recent = events.since(user.id(),
                            clock.instant().minus(Duration.ofDays(SecurityEvent.RETENTION_DAYS))).stream()
                    .map(event -> new Event(event.kind().name(), event.kind().description(), event.occurredAt()))
                    .toList();
            return new Overview(user.hasRecovery(),
                    email.map(found -> found.address().masked()).orElse(null),
                    email.map(AccountEmail::isVerified).orElse(false), recent);
        });
    }

    /**
     * Troca a senha e encerra todas as sessões, inclusive a atual, que é reaberta na resposta: quem estava usando a
     * senha vazada perde o acesso na hora.
     */
    public AuthenticatedSession changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = unitOfWork.execute(() -> load(userId));
        credentials.require(user, currentPassword);
        var newHash = hasher.hash(PasswordPolicy.requireAcceptable(newPassword, user.username()));

        AuthenticatedSession session = unitOfWork.execute(() -> {
            User current = load(userId);
            current.changePassword(newHash, clock);
            users.save(current);
            tokens.deletePending(current.id(), AccountToken.Purpose.RESET_PASSWORD);
            refreshTokens.revokeAllOfUser(userId, clock.instant());
            return sessionIssuer.startSession(current);
        });
        security.recorded(user, SecurityEvent.Kind.PASSWORD_CHANGED);
        return session;
    }

    /**
     * Cadastra, troca ou (com {@code newEmail} vazio) remove o e-mail. Um endereço novo só vale para recuperar a senha
     * depois de confirmado pelo link enviado a ele. O endereço antigo, se confirmado, recebe o aviso da troca.
     */
    public Overview changeEmail(UUID userId, String currentPassword, String newEmail) {
        User user = unitOfWork.execute(() -> load(userId));
        credentials.require(user, currentPassword);
        Optional<EmailAddress> requested = newEmail == null || newEmail.isBlank()
                ? Optional.empty()
                : Optional.of(new EmailAddress(newEmail));

        Optional<AccountEmail> previous = unitOfWork.execute(() -> emails.find(user.id()));
        if (previous.map(AccountEmail::address).equals(requested)) {
            return overview(userId);
        }
        Optional<AccountToken.Issued> verification = unitOfWork.execute(() -> {
            quota.consume(userId, ActionLimits.EMAIL_CHANGES);
            tokens.deletePending(user.id(), AccountToken.Purpose.VERIFY_EMAIL);
            tokens.deletePending(user.id(), AccountToken.Purpose.RESET_PASSWORD);
            if (requested.isEmpty()) {
                emails.delete(user.id());
                return Optional.empty();
            }
            emails.save(AccountEmail.unverified(user.id(), requested.get()));
            AccountToken.Issued issued = AccountToken.issue(user.id(), AccountToken.Purpose.VERIFY_EMAIL,
                    clock.instant());
            tokens.save(issued.token());
            return Optional.of(issued);
        });
        verification.ifPresent(issued ->
                mailer.sendEmailVerification(requested.get(), user.username().value(), issued.rawValue()));

        SecurityEvent.Kind kind = requested.isEmpty() ? SecurityEvent.Kind.EMAIL_REMOVED
                : previous.isEmpty() ? SecurityEvent.Kind.EMAIL_ADDED
                : SecurityEvent.Kind.EMAIL_CHANGED;
        security.notify(user, kind, previous);
        return overview(userId);
    }

    /** Reenvia o link de confirmação (no máximo um a cada {@link #RESEND_INTERVAL}). */
    public void resendVerification(UUID userId) {
        Optional<Resend> resend = unitOfWork.execute(() -> {
            User user = load(userId);
            Optional<AccountEmail> email = emails.find(user.id()).filter(found -> !found.isVerified());
            if (email.isEmpty()) {
                throw new DomainException("Não há e-mail esperando confirmação");
            }
            Instant now = clock.instant();
            boolean tooSoon = tokens.lastIssuedAt(user.id(), AccountToken.Purpose.VERIFY_EMAIL)
                    .map(last -> now.isBefore(last.plus(RESEND_INTERVAL)))
                    .orElse(false);
            if (tooSoon) {
                throw new DomainException("Acabamos de enviar um link. Espere alguns minutos para pedir outro");
            }
            tokens.deletePending(user.id(), AccountToken.Purpose.VERIFY_EMAIL);
            AccountToken.Issued issued = AccountToken.issue(user.id(), AccountToken.Purpose.VERIFY_EMAIL, now);
            tokens.save(issued.token());
            return Optional.of(new Resend(email.get().address(), user.username().value(), issued.rawValue()));
        });
        resend.ifPresent(found -> mailer.sendEmailVerification(found.email(), found.username(), found.token()));
    }

    /** Confirma o e-mail pelo link. Não exige sessão: o link pode ser aberto em outro aparelho. */
    public void verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidCredentialsException("Link inválido ou expirado");
        }
        User user = unitOfWork.execute(() -> {
            Instant now = clock.instant();
            AccountToken token = tokens.findByHash(AccountToken.hashOf(rawToken.strip()))
                    .filter(found -> found.isUsable(AccountToken.Purpose.VERIFY_EMAIL, now))
                    .orElseThrow(() -> new InvalidCredentialsException("Link inválido ou expirado"));
            AccountEmail email = emails.find(token.userId())
                    .orElseThrow(() -> new InvalidCredentialsException("Link inválido ou expirado"));
            token.markUsed(now);
            tokens.save(token);
            email.verify(now);
            emails.save(email);
            return users.findById(token.userId()).orElseThrow();
        });
        security.recorded(user, SecurityEvent.Kind.EMAIL_VERIFIED);
    }

    private User load(UUID userId) {
        return users.findById(new UserId(userId)).orElseThrow(InvalidCredentialsException::new);
    }

    /**
     * @param email endereço mascarado ({@code br***@gm***.com}); o completo nunca volta para a tela
     */
    public record Overview(boolean hasRecoveryPhrase, String email, boolean emailVerified,
                           List<Event> events) {
    }

    public record Event(String kind, String description, Instant occurredAt) {
    }

    private record Resend(EmailAddress email, String username, String token) {
    }
}
