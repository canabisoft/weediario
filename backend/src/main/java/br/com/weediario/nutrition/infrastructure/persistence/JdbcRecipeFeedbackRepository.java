package br.com.weediario.nutrition.infrastructure.persistence;

import br.com.weediario.nutrition.domain.model.CommentReaction;
import br.com.weediario.nutrition.domain.model.RecipeComment;
import br.com.weediario.nutrition.domain.model.RecipeId;
import br.com.weediario.nutrition.domain.repository.RecipeFeedbackRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class JdbcRecipeFeedbackRepository implements RecipeFeedbackRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcRecipeFeedbackRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void rate(RecipeId recipeId, UUID raterId, int stars, Instant ratedAt) {
        jdbc.update("""
                insert into recipe_ratings (recipe_id, rater_id, stars, rated_at)
                values (:recipeId, :raterId, :stars, :ratedAt)
                on conflict (recipe_id, rater_id) do update set stars = excluded.stars, rated_at = excluded.rated_at""",
                new MapSqlParameterSource("recipeId", recipeId.value()).addValue("raterId", raterId)
                        .addValue("stars", stars).addValue("ratedAt", Timestamp.from(ratedAt)));
    }

    @Override
    public void removeRating(RecipeId recipeId, UUID raterId) {
        jdbc.update("delete from recipe_ratings where recipe_id = :recipeId and rater_id = :raterId",
                new MapSqlParameterSource("recipeId", recipeId.value()).addValue("raterId", raterId));
    }

    @Override
    public Optional<Integer> ratingOf(RecipeId recipeId, UUID raterId) {
        return jdbc.queryForList("select stars from recipe_ratings where recipe_id = :recipeId and rater_id = :raterId",
                        new MapSqlParameterSource("recipeId", recipeId.value()).addValue("raterId", raterId), Integer.class)
                .stream().findFirst();
    }

    @Override
    public RecipeComment save(RecipeComment comment) {
        jdbc.update("""
                insert into recipe_comments (id, recipe_id, parent_id, author_id, body, hidden, created_at)
                values (:id, :recipeId, :parentId, :authorId, :body, :hidden, :createdAt)
                on conflict (id) do update set hidden = excluded.hidden""",
                new MapSqlParameterSource()
                        .addValue("id", comment.id())
                        .addValue("recipeId", comment.recipeId().value())
                        .addValue("parentId", comment.parentId().orElse(null), Types.OTHER)
                        .addValue("authorId", comment.authorId())
                        .addValue("body", comment.body())
                        .addValue("hidden", comment.hidden())
                        .addValue("createdAt", Timestamp.from(comment.createdAt())));
        return comment;
    }

    @Override
    public boolean commentAuthorActive(UUID commentId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select 1 from recipe_comments c join users u on u.id = c.author_id
                                where c.id = :id and u.status = 'ACTIVE')""",
                new MapSqlParameterSource("id", commentId), Boolean.class));
    }

    @Override
    public Optional<RecipeComment> findComment(UUID commentId) {
        return jdbc.query("select * from recipe_comments where id = :id", new MapSqlParameterSource("id", commentId),
                        (rs, n) -> RecipeComment.restore(rs.getObject("id", UUID.class),
                                new RecipeId(rs.getObject("recipe_id", UUID.class)), rs.getObject("parent_id", UUID.class),
                                rs.getObject("author_id", UUID.class),
                                rs.getString("body"), rs.getBoolean("hidden"), rs.getTimestamp("created_at").toInstant()))
                .stream().findFirst();
    }

    @Override
    public void deleteComment(UUID commentId) {
        jdbc.update("delete from recipe_comments where id = :id", new MapSqlParameterSource("id", commentId));
    }

    @Override
    public long countCommentsByAuthorSince(UUID authorId, Instant since) {
        Long count = jdbc.queryForObject(
                "select count(*) from recipe_comments where author_id = :authorId and created_at >= :since",
                new MapSqlParameterSource("authorId", authorId).addValue("since", Timestamp.from(since)), Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public void addCommentReaction(UUID commentId, String reactor, CommentReaction type) {
        jdbc.update("""
                insert into recipe_comment_reactions (comment_id, reactor, type) values (:commentId, :reactor, :type)
                on conflict do nothing""", reactionParams(commentId, reactor, type));
    }

    @Override
    public void removeCommentReaction(UUID commentId, String reactor, CommentReaction type) {
        jdbc.update("""
                delete from recipe_comment_reactions
                 where comment_id = :commentId and reactor = :reactor and type = :type""",
                reactionParams(commentId, reactor, type));
    }

    @Override
    public Map<UUID, Map<CommentReaction, Long>> commentReactionCounts(Collection<UUID> commentIds) {
        Map<UUID, Map<CommentReaction, Long>> counts = new HashMap<>();
        if (commentIds.isEmpty()) {
            return counts;
        }
        jdbc.query("""
                select comment_id, type, count(*) as total from recipe_comment_reactions
                 where comment_id in (:ids) group by comment_id, type""",
                new MapSqlParameterSource("ids", List.copyOf(commentIds)),
                rs -> {
                    counts.computeIfAbsent(rs.getObject("comment_id", UUID.class), id -> new EnumMap<>(CommentReaction.class))
                            .put(CommentReaction.valueOf(rs.getString("type")), rs.getLong("total"));
                });
        return counts;
    }

    @Override
    public Map<UUID, Set<CommentReaction>> commentReactionsBy(Map<UUID, String> reactorByComment) {
        Map<UUID, Set<CommentReaction>> mine = new HashMap<>();
        if (reactorByComment.isEmpty()) {
            return mine;
        }
        jdbc.query("""
                select comment_id, reactor, type from recipe_comment_reactions
                 where comment_id in (:ids) and reactor in (:reactors)""",
                new MapSqlParameterSource("ids", List.copyOf(reactorByComment.keySet()))
                        .addValue("reactors", List.copyOf(reactorByComment.values())),
                rs -> {
                    UUID commentId = rs.getObject("comment_id", UUID.class);
                    if (reactorByComment.get(commentId).equals(rs.getString("reactor"))) {
                        mine.computeIfAbsent(commentId, id -> EnumSet.noneOf(CommentReaction.class))
                                .add(CommentReaction.valueOf(rs.getString("type")));
                    }
                });
        return mine;
    }

    private static MapSqlParameterSource reactionParams(UUID commentId, String reactor, CommentReaction type) {
        return new MapSqlParameterSource("commentId", commentId).addValue("reactor", reactor).addValue("type", type.name());
    }
}
