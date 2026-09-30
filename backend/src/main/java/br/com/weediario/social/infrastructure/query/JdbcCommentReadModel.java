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
                select c.id, u.username, u.avatar_image_id is not null as has_avatar,
                       c.author_id = d.owner_id as by_diary_author, c.body, c.created_at,
                       coalesce(c.author_id = :viewerId or d.owner_id = :viewerId, false) as deletable
                """ + FROM + " order by c.created_at, c.id limit :limit offset :offset",
                params,
                (rs, rowNum) -> new CommentView(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        rs.getBoolean("has_avatar"),
                        rs.getBoolean("by_diary_author"),
                        rs.getString("body"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getBoolean("deletable")));
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    @Override
    public PageResult<ReportedComment> reportedComments(PageQuery page) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", page.size()).addValue("offset", page.offset());
        Long total = jdbc.queryForObject(
                "select count(*) from diary_comments c where exists (select 1 from comment_reports r where r.comment_id = c.id)",
                params, Long.class);
        List<ReportedComment> items = jdbc.query("""
                select c.id, c.diary_id, d.title, u.username, c.body, c.hidden, c.created_at, r.reports
                  from diary_comments c
                  join (select comment_id, count(*) as reports from comment_reports group by comment_id) r
                    on r.comment_id = c.id
                  join users u on u.id = c.author_id
                  join grow_diaries d on d.id = c.diary_id
                 order by c.hidden desc, r.reports desc, c.created_at desc, c.id
                 limit :limit offset :offset""",
                params,
                (rs, rowNum) -> new ReportedComment(
                        rs.getObject("id", UUID.class),
                        rs.getObject("diary_id", UUID.class),
                        rs.getString("title"),
                        rs.getString("username"),
                        rs.getString("body"),
                        rs.getLong("reports"),
                        rs.getBoolean("hidden"),
                        rs.getTimestamp("created_at").toInstant()));
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    @Override
    public ReportCounts reportCounts() {
        return jdbc.queryForObject("""
                select count(*) as reported, count(*) filter (where c.hidden) as hidden
                  from diary_comments c
                 where exists (select 1 from comment_reports r where r.comment_id = c.id)""",
                new MapSqlParameterSource(),
                (rs, rowNum) -> new ReportCounts(rs.getLong("reported"), rs.getLong("hidden")));
    }
}
