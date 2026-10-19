package br.com.weediario.notification.infrastructure.query;

import br.com.weediario.notification.application.query.NotificationReadModel;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class JdbcNotificationReadModel implements NotificationReadModel {

    /** Diário ou receita que ficou privado (e não é do destinatário) ou autor suspenso: a notificação some. */
    private static final String VISIBLE = """
              from notifications n
              left join grow_diaries d on d.id = n.diary_id
              left join recipes r on r.id = n.recipe_id
              left join users a on a.id = n.actor_id
             where n.recipient_id = :recipient
               and (n.diary_id is null or d.visibility in ('PUBLIC', 'COMMUNITY') or d.owner_id = :recipient)
               and (n.recipe_id is null or r.visibility = 'PUBLIC' or r.owner_id = :recipient)
               and (n.actor_id is null or a.status = 'ACTIVE')
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcNotificationReadModel(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<NotificationView> inbox(UUID recipientId, PageQuery page) {
        MapSqlParameterSource params = new MapSqlParameterSource("recipient", recipientId)
                .addValue("limit", page.size())
                .addValue("offset", page.offset());
        Long total = jdbc.queryForObject("select count(*) " + VISIBLE, params, Long.class);
        List<NotificationView> items = jdbc.query("""
                select n.id, n.type, n.diary_id, d.title, n.recipe_id, r.name as recipe_name, a.username, n.occurrences, n.created_at,
                       n.read_at is not null as read
                """ + VISIBLE + " order by n.created_at desc, n.id limit :limit offset :offset",
                params,
                (rs, rowNum) -> new NotificationView(
                        rs.getObject("id", UUID.class),
                        rs.getString("type"),
                        rs.getObject("diary_id", UUID.class),
                        rs.getString("title"),
                        rs.getObject("recipe_id", UUID.class),
                        rs.getString("recipe_name"),
                        rs.getString("username"),
                        rs.getInt("occurrences"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getBoolean("read")));
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    @Override
    public long unreadCount(UUID recipientId) {
        Long count = jdbc.queryForObject("select count(*) " + VISIBLE + " and n.read_at is null",
                new MapSqlParameterSource("recipient", recipientId), Long.class);
        return count == null ? 0 : count;
    }
}
