package br.com.weediario.identity.infrastructure.persistence;

import br.com.weediario.identity.domain.model.RefreshToken;
import br.com.weediario.identity.domain.model.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Deliberadamente sem IP, user-agent ou "dispositivo": a sessão é identificada apenas pelo hash do token.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshTokenJpaEntity() {
    }

    static RefreshTokenJpaEntity fromDomain(RefreshToken token) {
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity();
        entity.id = token.id();
        entity.userId = token.userId().value();
        entity.familyId = token.familyId();
        entity.tokenHash = token.tokenHash();
        entity.expiresAt = token.expiresAt();
        entity.createdAt = token.createdAt();
        entity.revokedAt = token.revokedAt();
        return entity;
    }

    RefreshToken toDomain() {
        return RefreshToken.restore(id, new UserId(userId), familyId, tokenHash, expiresAt, createdAt, revokedAt);
    }
}
