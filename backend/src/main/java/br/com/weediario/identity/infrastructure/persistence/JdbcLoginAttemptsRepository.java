package br.com.weediario.identity.infrastructure.persistence;

import br.com.weediario.identity.domain.model.LoginAttempts;
import br.com.weediario.identity.domain.repository.LoginAttemptsRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class JdbcLoginAttemptsRepository implements LoginAttemptsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcLoginAttemptsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LoginAttempts> find(String key) {
        return jdbc.query("select * from login_attempts where attempt_key = :key", new MapSqlParameterSource("key", key),
                (rs, rowNum) -> new LoginAttempts(
                        rs.getInt("failures"),
                        rs.getTimestamp("window_started_at").toInstant(),
                        rs.getTimestamp("locked_until") == null ? null : rs.getTimestamp("locked_until").toInstant(),
                        rs.getInt("lock_count"))).stream().findFirst();
    }

    @Override
    public void save(String key, LoginAttempts attempts) {
        jdbc.update("""
                insert into login_attempts (attempt_key, failures, window_started_at, locked_until, lock_count, updated_at)
                values (:key, :failures, :windowStartedAt, :lockedUntil, :lockCount, now())
                on conflict (attempt_key) do update set
                    failures = excluded.failures,
                    window_started_at = excluded.window_started_at,
                    locked_until = excluded.locked_until,
                    lock_count = excluded.lock_count,
                    updated_at = excluded.updated_at""",
                new MapSqlParameterSource("key", key)
                        .addValue("failures", attempts.failures())
                        .addValue("windowStartedAt", Timestamp.from(attempts.windowStartedAt()))
                        .addValue("lockedUntil", attempts.lockedUntil() == null ? null : Timestamp.from(attempts.lockedUntil()))
                        .addValue("lockCount", attempts.lockCount()));
    }

    @Override
    public void delete(String key) {
        jdbc.update("delete from login_attempts where attempt_key = :key", new MapSqlParameterSource("key", key));
    }

    @Override
    public int deleteStaleBefore(Instant cutoff) {
        return jdbc.update("""
                delete from login_attempts
                where updated_at < :cutoff and (locked_until is null or locked_until < :cutoff)""",
                new MapSqlParameterSource("cutoff", Timestamp.from(cutoff)));
    }
}
