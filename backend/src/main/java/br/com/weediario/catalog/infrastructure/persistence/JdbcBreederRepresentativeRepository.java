package br.com.weediario.catalog.infrastructure.persistence;

import br.com.weediario.catalog.domain.model.BreederRepresentative;
import br.com.weediario.catalog.domain.model.BreederRepresentative.Status;
import br.com.weediario.catalog.domain.repository.BreederRepresentativeRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcBreederRepresentativeRepository implements BreederRepresentativeRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcBreederRepresentativeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<BreederRepresentative> find(UUID breederId, UUID userId) {
        return jdbc.query("select * from breeder_representatives where breeder_id = :breederId and user_id = :userId",
                key(breederId, userId),
                (rs, n) -> new BreederRepresentative(
                        rs.getObject("breeder_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        Status.valueOf(rs.getString("status")),
                        rs.getString("note"),
                        rs.getTimestamp("requested_at").toInstant(),
                        rs.getTimestamp("decided_at") == null ? null : rs.getTimestamp("decided_at").toInstant()))
                .stream().findFirst();
    }

    @Override
    public BreederRepresentative save(BreederRepresentative representative) {
        jdbc.update("""
                insert into breeder_representatives (breeder_id, user_id, status, note, requested_at, decided_at)
                values (:breederId, :userId, :status, :note, :requestedAt, :decidedAt)
                on conflict (breeder_id, user_id) do update set
                    status = excluded.status, decided_at = excluded.decided_at""",
                key(representative.breederId(), representative.userId())
                        .addValue("status", representative.status().name())
                        .addValue("note", representative.note())
                        .addValue("requestedAt", Timestamp.from(representative.requestedAt()))
                        .addValue("decidedAt", representative.decidedAt() == null ? null
                                : Timestamp.from(representative.decidedAt()), Types.TIMESTAMP));
        return representative;
    }

    @Override
    public boolean delete(UUID breederId, UUID userId) {
        return jdbc.update("delete from breeder_representatives where breeder_id = :breederId and user_id = :userId",
                key(breederId, userId)) > 0;
    }

    @Override
    public long countPendingBy(UUID userId) {
        Long count = jdbc.queryForObject(
                "select count(*) from breeder_representatives where user_id = :userId and status = 'PENDING'",
                new MapSqlParameterSource("userId", userId), Long.class);
        return count == null ? 0 : count;
    }

    private static MapSqlParameterSource key(UUID breederId, UUID userId) {
        return new MapSqlParameterSource("breederId", breederId).addValue("userId", userId);
    }
}
