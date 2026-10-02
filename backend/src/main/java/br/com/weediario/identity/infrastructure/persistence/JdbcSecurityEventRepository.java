package br.com.weediario.identity.infrastructure.persistence;

import br.com.weediario.identity.domain.model.SecurityEvent;
import br.com.weediario.identity.domain.model.UserId;
import br.com.weediario.identity.domain.repository.SecurityEventRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcSecurityEventRepository implements SecurityEventRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcSecurityEventRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(SecurityEvent event) {
        jdbc.update("insert into security_events (id, user_id, kind, occurred_at) values (:id, :userId, :kind, :at)",
                new MapSqlParameterSource("id", UUID.randomUUID())
                        .addValue("userId", event.userId().value())
                        .addValue("kind", event.kind().name())
                        .addValue("at", Timestamp.from(event.occurredAt())));
    }

    @Override
    public List<SecurityEvent> since(UserId userId, Instant since) {
        return jdbc.query("""
                select kind, occurred_at from security_events
                 where user_id = :userId and occurred_at >= :since
                 order by occurred_at desc
                 limit 50""",
                new MapSqlParameterSource("userId", userId.value()).addValue("since", Timestamp.from(since)),
                (rs, rowNum) -> new SecurityEvent(userId, SecurityEvent.Kind.valueOf(rs.getString("kind")),
                        rs.getTimestamp("occurred_at").toInstant()));
    }

    @Override
    public void deleteBefore(Instant instant) {
        jdbc.update("delete from security_events where occurred_at < :instant",
                new MapSqlParameterSource("instant", Timestamp.from(instant)));
    }
}
