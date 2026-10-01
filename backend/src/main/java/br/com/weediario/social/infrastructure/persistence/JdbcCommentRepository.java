package br.com.weediario.social.infrastructure.persistence;

import br.com.weediario.social.domain.model.Comment;
import br.com.weediario.social.domain.repository.CommentRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCommentRepository implements CommentRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCommentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Comment save(Comment comment) {
        jdbc.update("""
                insert into diary_comments (id, diary_id, parent_id, author_id, body, hidden, created_at)
                values (:id, :diaryId, :parentId, :authorId, :body, :hidden, :createdAt)
                on conflict (id) do update set hidden = excluded.hidden""",
                new MapSqlParameterSource()
                        .addValue("id", comment.id())
                        .addValue("diaryId", comment.diaryId())
                        .addValue("parentId", comment.parentId().orElse(null), Types.OTHER)
                        .addValue("authorId", comment.authorId())
                        .addValue("body", comment.body())
                        .addValue("hidden", comment.hidden())
                        .addValue("createdAt", Timestamp.from(comment.createdAt())));
        return comment;
    }

    @Override
    public Optional<Comment> findById(UUID id) {
        return jdbc.query("select * from diary_comments where id = :id", new MapSqlParameterSource("id", id), MAPPER)
                .stream().findFirst();
    }

    @Override
    public boolean authorActive(UUID commentId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select 1 from diary_comments c join users u on u.id = c.author_id
                                where c.id = :id and u.status = 'ACTIVE')""",
                new MapSqlParameterSource("id", commentId), Boolean.class));
    }

    @Override
    public void delete(UUID id) {
        jdbc.update("delete from diary_comments where id = :id", new MapSqlParameterSource("id", id));
    }

    @Override
    public long countByAuthorSince(UUID authorId, Instant since) {
        Long count = jdbc.queryForObject(
                "select count(*) from diary_comments where author_id = :authorId and created_at >= :since",
                new MapSqlParameterSource("authorId", authorId).addValue("since", Timestamp.from(since)), Long.class);
        return count == null ? 0 : count;
    }

    private static final RowMapper<Comment> MAPPER = (rs, rowNum) -> Comment.restore(
            rs.getObject("id", UUID.class),
            rs.getObject("diary_id", UUID.class),
            rs.getObject("parent_id", UUID.class),
            rs.getObject("author_id", UUID.class),
            rs.getString("body"),
            rs.getBoolean("hidden"),
            rs.getTimestamp("created_at").toInstant());
}
