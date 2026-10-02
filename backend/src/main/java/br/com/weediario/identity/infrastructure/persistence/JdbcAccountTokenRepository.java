package br.com.weediario.identity.infrastructure.persistence;

import br.com.weediario.identity.domain.model.AccountToken;
import br.com.weediario.identity.domain.model.UserId;
import br.com.weediario.identity.domain.repository.AccountTokenRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAccountTokenRepository implements AccountTokenRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAccountTokenRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(AccountToken token) {
        jdbc.update("""
                insert into account_tokens (id, user_id, purpose, token_hash, created_at, expires_at, used_at)
                values (:id, :userId, :purpose, :hash, :createdAt, :expiresAt, :usedAt)
                on conflict (id) do update set used_at = excluded.used_at""",
                new MapSqlParameterSource("id", token.id())
                        .addValue("userId", token.userId().value())
                        .addValue("purpose", token.purpose().name())
                        .addValue("hash", token.tokenHash())
                        .addValue("createdAt", Timestamp.from(token.createdAt()))
                        .addValue("expiresAt", Timestamp.from(token.expiresAt()))
                        .addValue("usedAt", token.usedAt() == null ? null : Timestamp.from(token.usedAt())));
    }

    @Override
    public Optional<AccountToken> findByHash(String tokenHash) {
        return jdbc.query("select * from account_tokens where token_hash = :hash for update",
                new MapSqlParameterSource("hash", tokenHash),
                (rs, rowNum) -> AccountToken.restore(
                        rs.getObject("id", UUID.class),
                        new UserId(rs.getObject("user_id", UUID.class)),
                        AccountToken.Purpose.valueOf(rs.getString("purpose")),
                        rs.getString("token_hash"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("used_at") == null ? null : rs.getTimestamp("used_at").toInstant()))
                .stream().findFirst();
    }

    @Override
    public Optional<Instant> lastIssuedAt(UserId userId, AccountToken.Purpose purpose) {
        Timestamp last = jdbc.queryForObject(
                "select max(created_at) from account_tokens where user_id = :userId and purpose = :purpose",
                new MapSqlParameterSource("userId", userId.value()).addValue("purpose", purpose.name()),
                Timestamp.class);
        return Optional.ofNullable(last).map(Timestamp::toInstant);
    }

    @Override
    public void deletePending(UserId userId, AccountToken.Purpose purpose) {
        jdbc.update("delete from account_tokens where user_id = :userId and purpose = :purpose and used_at is null",
                new MapSqlParameterSource("userId", userId.value()).addValue("purpose", purpose.name()));
    }

    @Override
    public void deleteExpiredBefore(Instant instant) {
        jdbc.update("delete from account_tokens where expires_at < :instant",
                new MapSqlParameterSource("instant", Timestamp.from(instant)));
    }
}
