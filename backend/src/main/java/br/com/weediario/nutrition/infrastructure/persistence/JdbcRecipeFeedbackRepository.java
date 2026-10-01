package br.com.weediario.nutrition.infrastructure.persistence;

import br.com.weediario.nutrition.domain.model.RecipeComment;
import br.com.weediario.nutrition.domain.model.RecipeId;
import br.com.weediario.nutrition.domain.repository.RecipeFeedbackRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
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
                insert into recipe_comments (id, recipe_id, author_id, body, hidden, created_at)
                values (:id, :recipeId, :authorId, :body, :hidden, :createdAt)
                on conflict (id) do update set hidden = excluded.hidden""",
                new MapSqlParameterSource()
                        .addValue("id", comment.id())
                        .addValue("recipeId", comment.recipeId().value())
                        .addValue("authorId", comment.authorId())
                        .addValue("body", comment.body())
                        .addValue("hidden", comment.hidden())
                        .addValue("createdAt", Timestamp.from(comment.createdAt())));
        return comment;
    }

    @Override
    public Optional<RecipeComment> findComment(UUID commentId) {
        return jdbc.query("select * from recipe_comments where id = :id", new MapSqlParameterSource("id", commentId),
                        (rs, n) -> RecipeComment.restore(rs.getObject("id", UUID.class),
                                new RecipeId(rs.getObject("recipe_id", UUID.class)), rs.getObject("author_id", UUID.class),
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
    public long report(UUID commentId, UUID reporterId, Instant reportedAt) {
        MapSqlParameterSource params = new MapSqlParameterSource("commentId", commentId)
                .addValue("reporterId", reporterId).addValue("reportedAt", Timestamp.from(reportedAt));
        jdbc.update("""
                insert into recipe_comment_reports (comment_id, reporter_id, reported_at)
                values (:commentId, :reporterId, :reportedAt)
                on conflict do nothing""", params);
        Long count = jdbc.queryForObject("select count(*) from recipe_comment_reports where comment_id = :commentId",
                params, Long.class);
        return count == null ? 0 : count;
    }
}
