package br.com.weediario.social.infrastructure.persistence;

import br.com.weediario.social.domain.model.ReactionType;
import br.com.weediario.social.domain.model.ReactorPseudonym;
import br.com.weediario.social.domain.repository.CommentReactionRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
public class JdbcCommentReactionRepository implements CommentReactionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCommentReactionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(UUID commentId, ReactorPseudonym reactor, ReactionType type) {
        jdbc.update("""
                insert into diary_comment_reactions (comment_id, reactor, type)
                values (:commentId, :reactor, :type)
                on conflict do nothing""", params(commentId, reactor, type));
    }

    @Override
    public void remove(UUID commentId, ReactorPseudonym reactor, ReactionType type) {
        jdbc.update("""
                delete from diary_comment_reactions
                 where comment_id = :commentId and reactor = :reactor and type = :type""",
                params(commentId, reactor, type));
    }

    @Override
    public Map<UUID, Map<ReactionType, Long>> countByType(Collection<UUID> commentIds) {
        Map<UUID, Map<ReactionType, Long>> counts = new HashMap<>();
        if (commentIds.isEmpty()) {
            return counts;
        }
        jdbc.query("""
                select comment_id, type, count(*) as total from diary_comment_reactions
                 where comment_id in (:ids) group by comment_id, type""",
                new MapSqlParameterSource("ids", List.copyOf(commentIds)),
                rs -> {
                    counts.computeIfAbsent(rs.getObject("comment_id", UUID.class), id -> new EnumMap<>(ReactionType.class))
                            .put(ReactionType.valueOf(rs.getString("type")), rs.getLong("total"));
                });
        return counts;
    }

    @Override
    public Map<UUID, Set<ReactionType>> typesBy(Map<UUID, ReactorPseudonym> reactorByComment) {
        Map<UUID, Set<ReactionType>> mine = new HashMap<>();
        if (reactorByComment.isEmpty()) {
            return mine;
        }
        // O pseudônimo de quem está vendo é diferente em cada comentário: confere o par (comentário, pseudônimo).
        List<String> reactors = reactorByComment.values().stream().map(ReactorPseudonym::value).toList();
        jdbc.query("""
                select comment_id, reactor, type from diary_comment_reactions
                 where comment_id in (:ids) and reactor in (:reactors)""",
                new MapSqlParameterSource("ids", List.copyOf(reactorByComment.keySet())).addValue("reactors", reactors),
                rs -> {
                    UUID commentId = rs.getObject("comment_id", UUID.class);
                    if (reactorByComment.get(commentId).value().equals(rs.getString("reactor"))) {
                        mine.computeIfAbsent(commentId, id -> EnumSet.noneOf(ReactionType.class))
                                .add(ReactionType.valueOf(rs.getString("type")));
                    }
                });
        return mine;
    }

    private static MapSqlParameterSource params(UUID commentId, ReactorPseudonym reactor, ReactionType type) {
        return new MapSqlParameterSource("commentId", commentId).addValue("reactor", reactor.value())
                .addValue("type", type.name());
    }
}
