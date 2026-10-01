package br.com.weediario.identity.infrastructure.persistence;

import br.com.weediario.identity.domain.model.RefreshToken;
import br.com.weediario.identity.domain.repository.RefreshTokenRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaRefreshTokenRepository implements RefreshTokenRepository {

    private final SpringDataRefreshTokenRepository jpa;

    public JpaRefreshTokenRepository(SpringDataRefreshTokenRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        return jpa.save(RefreshTokenJpaEntity.fromDomain(token)).toDomain();
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(RefreshTokenJpaEntity::toDomain);
    }

    @Override
    public void revokeFamily(UUID familyId, Instant now) {
        jpa.revokeFamily(familyId, now);
    }

    @Override
    public boolean hasActiveToken(UUID familyId, Instant now) {
        return jpa.existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(familyId, now);
    }
}
