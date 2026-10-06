package br.com.weediario.identity.infrastructure.persistence;

import br.com.weediario.identity.domain.model.RefreshToken;
import br.com.weediario.identity.domain.repository.RefreshTokenRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaRefreshTokenRepository implements RefreshTokenRepository {

    private final SpringDataRefreshTokenRepository jpa;
    private final NamedParameterJdbcTemplate jdbc;

    public JpaRefreshTokenRepository(SpringDataRefreshTokenRepository jpa, NamedParameterJdbcTemplate jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
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
    public void revokeAllOfUser(UUID userId, Instant now) {
        jpa.revokeAllOfUser(userId, now);
        // Também os access tokens já emitidos: o AccountStatusFilter recusa os de geração anterior. A sessão aberta
        // logo em seguida, na mesma transação, já sai com a geração nova.
        jdbc.update("update users set session_epoch = session_epoch + 1 where id = :id",
                new MapSqlParameterSource("id", userId));
    }

    @Override
    public boolean hasActiveToken(UUID familyId, Instant now) {
        return jpa.existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(familyId, now);
    }
}
