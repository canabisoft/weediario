package br.com.weediario.review.infrastructure.persistence;

import br.com.weediario.review.domain.model.Seedbank;
import br.com.weediario.review.domain.repository.SeedbankRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcSeedbankRepository implements SeedbankRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcSeedbankRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Seedbank save(Seedbank seedbank) {
        jdbc.update("insert into seedbanks (id, name, name_key, created_at) values (:id, :name, :key, :createdAt)",
                new MapSqlParameterSource("id", seedbank.id())
                        .addValue("name", seedbank.name())
                        .addValue("key", seedbank.key())
                        .addValue("createdAt", Timestamp.from(seedbank.createdAt())));
        return seedbank;
    }

    @Override
    public Optional<Seedbank> findByKey(String key) {
        return jdbc.query("select * from seedbanks where name_key = :key", new MapSqlParameterSource("key", key),
                (rs, rowNum) -> Seedbank.restore(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("name_key"),
                        rs.getTimestamp("created_at").toInstant())).stream().findFirst();
    }
}
