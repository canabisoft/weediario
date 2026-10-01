package br.com.weediario.identity.domain.repository;

import br.com.weediario.identity.domain.model.RefreshToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void revokeFamily(UUID familyId, Instant now);

    /** Encerra todas as sessões da conta (suspensão ou banimento). */
    void revokeAllOfUser(UUID userId, Instant now);

    /** Se a sessão (família) ainda tem algum token não revogado e não expirado. */
    boolean hasActiveToken(UUID familyId, Instant now);
}
