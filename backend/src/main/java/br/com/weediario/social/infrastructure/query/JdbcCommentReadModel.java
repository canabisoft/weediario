package br.com.weediario.social.infrastructure.query;

import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.social.application.query.CommentReadModel;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Comentários de contas suspensas e comentários ocultados por denúncia ficam fora da listagem.
 */
@Component
public class JdbcCommentReadModel implements CommentReadModel {

    private static final String FROM = """
              from diary_comments c
              join users u on u.id = c.author_id and u.status = 'ACTIVE'
              join grow_diaries d on d.id = c.diary_id
             where c.diary_id = :diaryId and not c.hidden
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCommentReadModel(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<CommentView> visibleComments(UUID diaryId, Optional<UUID> viewerId, PageQuery page) {
        MapSqlParameterSource params = new MapSqlParameterSource("diaryId", diaryId)
                .addValue("viewerId", viewerId.orElse(null), Types.OTHER)
                .addValue("limit", page.size())
                .addValue("offset", page.offset());
        Long total = jdbc.queryForObject("select count(*) " + FROM, params, Long.class);
        List<CommentView> items = jdbc.query("""
                select c.id, u.username, c.author_id = d.owner_id as by_diary_author, c.body, c.created_at,
                       coalesce(c.author_id = :viewerId or d.owner_id = :viewerId, false) as deletable
                """ + FROM + " order by c.created_at, c.id limit :limit offset :offset",
                params,
                (rs, rowNum) -> new CommentView(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        rs.getBoolean("by_diary_author"),
                        rs.getString("body"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getBoolean("deletable")));
        return PageResult.of(items, page, total == null ? 0 : total);
    }
}
