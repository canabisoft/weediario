package br.com.weediario.social.infrastructure.persistence;

import br.com.weediario.social.domain.model.ReactionType;
import br.com.weediario.social.domain.model.ReactorPseudonym;
import br.com.weediario.social.domain.repository.ReactionRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
public class JdbcReactionRepository implements ReactionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcReactionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean add(UUID diaryId, ReactorPseudonym reactor, ReactionType type, Instant at) {
        return jdbc.update("""
                insert into diary_reactions (diary_id, reactor, type, created_at)
                values (:diaryId, :reactor, :type, :at)
                on conflict do nothing""",
                params(diaryId, reactor).addValue("type", type.name()).addValue("at", Timestamp.from(at))) > 0;
    }

    @Override
    public void remove(UUID diaryId, ReactorPseudonym reactor, ReactionType type) {
        jdbc.update("delete from diary_reactions where diary_id = :diaryId and reactor = :reactor and type = :type",
                params(diaryId, reactor).addValue("type", type.name()));
    }

    @Override
    public Map<ReactionType, Long> countByType(UUID diaryId) {
        Map<ReactionType, Long> counts = new EnumMap<>(ReactionType.class);
        jdbc.query("select type, count(*) as total from diary_reactions where diary_id = :diaryId group by type",
                new MapSqlParameterSource("diaryId", diaryId),
                rs -> {
                    counts.put(ReactionType.valueOf(rs.getString("type")), rs.getLong("total"));
                });
        return counts;
    }

    @Override
    public Set<ReactionType> typesBy(UUID diaryId, ReactorPseudonym reactor) {
        Set<ReactionType> types = EnumSet.noneOf(ReactionType.class);
        jdbc.query("select type from diary_reactions where diary_id = :diaryId and reactor = :reactor",
                params(diaryId, reactor),
                rs -> {
                    types.add(ReactionType.valueOf(rs.getString("type")));
                });
        return types;
    }

    private static MapSqlParameterSource params(UUID diaryId, ReactorPseudonym reactor) {
        return new MapSqlParameterSource("diaryId", diaryId).addValue("reactor", reactor.value());
    }
}
