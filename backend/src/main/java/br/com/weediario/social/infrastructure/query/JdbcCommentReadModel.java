package br.com.weediario.social.infrastructure.query;

import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.social.application.query.CommentReadModel;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Comentários de contas suspensas ficam fora da listagem; os ocultados pela moderação só aparecem para o autor.
 * Respostas só aparecem debaixo de um comentário raiz visível.
 */
@Component
public class JdbcCommentReadModel implements CommentReadModel {

    /** Comentário (raiz ou resposta) que quem está vendo pode ler, de autor ativo. */
    private static final String VISIBLE = """
              from diary_comments c
              join users u on u.id = c.author_id and u.status = 'ACTIVE'
              join grow_diaries d on d.id = c.diary_id
             where (not c.hidden or c.author_id = :viewerId)
            """;

    private static final String COLUMNS = """
            select c.id, c.parent_id, u.username, u.avatar_image_id is not null and not u.profile_hidden as has_avatar,
                   c.hidden, c.author_id = d.owner_id as by_diary_author, c.body, c.created_at,
                   coalesce(c.author_id = :viewerId or d.owner_id = :viewerId, false) as deletable
            """;

    private static final String ROOT_IS_VISIBLE = """
               and exists (select 1 from diary_comments p join users pu on pu.id = p.author_id and pu.status = 'ACTIVE'
                            where p.id = c.parent_id and p.parent_id is null
                              and (not p.hidden or p.author_id = :viewerId))
            """;

    private static final RowMapper<CommentView> MAPPER = (rs, rowNum) -> new CommentView(
            rs.getObject("id", UUID.class),
            rs.getObject("parent_id", UUID.class),
            rs.getString("username"),
            rs.getBoolean("has_avatar"),
            rs.getBoolean("by_diary_author"),
            rs.getString("body"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getBoolean("deletable"),
            rs.getBoolean("hidden"),
            null,
            0,
            List.of());

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCommentReadModel(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<CommentView> visibleComments(UUID diaryId, Optional<UUID> viewerId, PageQuery page) {
        MapSqlParameterSource params = viewer(viewerId)
                .addValue("diaryId", diaryId)
                .addValue("limit", page.size())
                .addValue("offset", page.offset());
        String where = VISIBLE + " and c.diary_id = :diaryId and c.parent_id is null";
        Long total = jdbc.queryForObject("select count(*) " + where, params, Long.class);
        List<CommentView> items = jdbc.query(COLUMNS + where + " order by c.created_at, c.id limit :limit offset :offset",
                params, MAPPER);
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    @Override
    public Map<UUID, Replies> replyPreviews(Collection<UUID> rootIds, Optional<UUID> viewerId, int perRoot) {
        if (rootIds.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = viewer(viewerId)
                .addValue("rootIds", List.copyOf(rootIds))
                .addValue("perRoot", perRoot);
        Map<UUID, Long> totals = new HashMap<>();
        Map<UUID, List<CommentView>> items = new LinkedHashMap<>();
        jdbc.query("""
                select * from (
                """ + COLUMNS + """
                       , row_number() over (partition by c.parent_id order by c.created_at, c.id) as position,
                       count(*) over (partition by c.parent_id) as total
                """ + VISIBLE + """
                   and c.parent_id in (:rootIds)
                ) x where x.position <= :perRoot order by x.created_at, x.id""",
                params,
                rs -> {
                    UUID rootId = rs.getObject("parent_id", UUID.class);
                    totals.put(rootId, rs.getLong("total"));
                    items.computeIfAbsent(rootId, id -> new ArrayList<>()).add(MAPPER.mapRow(rs, 0));
                });
        Map<UUID, Replies> previews = new HashMap<>();
        items.forEach((rootId, replies) -> previews.put(rootId, new Replies(List.copyOf(replies), totals.get(rootId))));
        return previews;
    }

    @Override
    public PageResult<CommentView> visibleReplies(UUID rootId, Optional<UUID> viewerId, PageQuery page) {
        MapSqlParameterSource params = viewer(viewerId)
                .addValue("rootId", rootId)
                .addValue("limit", page.size())
                .addValue("offset", page.offset());
        String where = VISIBLE + " and c.parent_id = :rootId" + ROOT_IS_VISIBLE;
        Long total = jdbc.queryForObject("select count(*) " + where, params, Long.class);
        List<CommentView> items = jdbc.query(COLUMNS + where + " order by c.created_at, c.id limit :limit offset :offset",
                params, MAPPER);
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    private static MapSqlParameterSource viewer(Optional<UUID> viewerId) {
        return new MapSqlParameterSource().addValue("viewerId", viewerId.orElse(null), Types.OTHER);
    }
}
