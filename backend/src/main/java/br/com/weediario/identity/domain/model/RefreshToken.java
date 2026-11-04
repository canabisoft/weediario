package br.com.weediario.identity.domain.model;

import br.com.weediario.shared.domain.Guard;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token opaco e rotativo. Só o hash (SHA-256) é persistido; o valor bruto existe apenas no cookie do cliente.
 * <p>
 * Tokens de uma mesma sessão compartilham a {@code familyId}: se um token já rotacionado for reapresentado
 * (sinal de roubo), a família inteira é revogada.
 * <p>
 * A rotação renova a validade, mas nunca além de {@link #MAX_SESSION_AGE} desde o login que abriu a família: quem usa o
 * app todo dia entra de novo a cada 90 dias, e um cookie roubado não vale para sempre.
 */
public final class RefreshToken {

    public static final Duration MAX_SESSION_AGE = Duration.ofDays(90);

    private final UUID id;
    private final UserId userId;
    private final UUID familyId;
    private final String tokenHash;
    private final Instant expiresAt;
    private final Instant createdAt;
    private final Instant familyStartedAt;
    private Instant revokedAt;

    private RefreshToken(UUID id, UserId userId, UUID familyId, String tokenHash,
                         Instant expiresAt, Instant createdAt, Instant familyStartedAt, Instant revokedAt) {
        this.id = Guard.notNull(id, "Id");
        this.userId = Guard.notNull(userId, "Usuário");
        this.familyId = Guard.notNull(familyId, "Família");
        this.tokenHash = Guard.notBlank(tokenHash, "Hash do token");
        this.expiresAt = Guard.notNull(expiresAt, "Expiração");
        this.createdAt = Guard.notNull(createdAt, "Data de criação");
        this.familyStartedAt = Guard.notNull(familyStartedAt, "Início da sessão");
        this.revokedAt = revokedAt;
    }

    public static RefreshToken startSession(UserId userId, String tokenHash, Instant now, Duration ttl) {
        return new RefreshToken(UUID.randomUUID(), userId, UUID.randomUUID(), tokenHash, now.plus(ttl), now, now, null);
    }

    public RefreshToken rotate(String newTokenHash, Instant now, Duration ttl) {
        revoke(now);
        Instant renewed = now.plus(ttl);
        Instant ceiling = familyStartedAt.plus(MAX_SESSION_AGE);
        return new RefreshToken(UUID.randomUUID(), userId, familyId, newTokenHash,
                renewed.isAfter(ceiling) ? ceiling : renewed, now, familyStartedAt, null);
    }

    public static RefreshToken restore(UUID id, UserId userId, UUID familyId, String tokenHash,
                                       Instant expiresAt, Instant createdAt, Instant familyStartedAt,
                                       Instant revokedAt) {
        return new RefreshToken(id, userId, familyId, tokenHash, expiresAt, createdAt, familyStartedAt, revokedAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** Revogado há no máximo {@code window} (inclusive). Falso se nunca foi revogado. */
    public boolean wasRevokedWithin(Duration window, Instant now) {
        return revokedAt != null && !now.isAfter(revokedAt.plus(window));
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public UUID id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public UUID familyId() {
        return familyId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant familyStartedAt() {
        return familyStartedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }
}
