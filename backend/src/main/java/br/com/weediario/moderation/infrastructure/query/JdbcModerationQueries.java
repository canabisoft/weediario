package br.com.weediario.moderation.infrastructure.query;

import br.com.weediario.moderation.application.query.ModerationQueries;
import br.com.weediario.moderation.domain.model.ContentRef;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.HoldCause;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.ReportReason;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.TargetType;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class JdbcModerationQueries implements ModerationQueries {

    /** Tudo que tem denúncia ou está retido, com a contagem e a data mais antiga. */
    private static final String PENDING = """
            with refs as (
                select target_type, target_id from content_reports
                union
                select target_type, target_id from moderation_holds
            )
            select r.target_type, r.target_id, h.cause, h.detail,
                   (select count(*) from content_reports c
                     where c.target_type = r.target_type and c.target_id = r.target_id) as reports,
                   coalesce(h.held_at, (select min(c.created_at) from content_reports c
                                         where c.target_type = r.target_type and c.target_id = r.target_id)) as since
              from refs r
              left join moderation_holds h on h.target_type = r.target_type and h.target_id = r.target_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcModerationQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<Pending> pending(PageQuery page) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", page.size()).addValue("offset", page.offset());
        Long total = jdbc.queryForObject("select count(*) from (" + PENDING + ") p", params, Long.class);
        List<Pending> items = jdbc.query("select * from (" + PENDING + """
                ) p
                 order by (p.cause is not null) desc, p.reports desc, p.since desc, p.target_id
                 limit :limit offset :offset""", params,
                (rs, n) -> new Pending(
                        new ContentRef(TargetType.valueOf(rs.getString("target_type")), rs.getObject("target_id", UUID.class)),
                        rs.getString("cause") == null ? null : HoldCause.valueOf(rs.getString("cause")),
                        rs.getString("detail"), rs.getLong("reports"), rs.getTimestamp("since").toInstant()));
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    @Override
    public Map<ReportReason, Long> reasons(ContentRef ref) {
        Map<ReportReason, Long> reasons = new EnumMap<>(ReportReason.class);
        jdbc.query("""
                select reason, count(*) as total from content_reports
                 where target_type = :type and target_id = :id group by reason""",
                new MapSqlParameterSource("type", ref.type().name()).addValue("id", ref.id()),
                rs -> {
                    reasons.put(ReportReason.valueOf(rs.getString("reason")), rs.getLong("total"));
                });
        return reasons;
    }

    @Override
    public Counts counts() {
        Map<TargetType, Long> byType = new EnumMap<>(TargetType.class);
        long[] totals = new long[2];
        jdbc.query("select target_type, count(*) as total, count(*) filter (where cause is not null) as held from ("
                        + PENDING + ") p group by target_type", new MapSqlParameterSource(),
                rs -> {
                    byType.put(TargetType.valueOf(rs.getString("target_type")), rs.getLong("total"));
                    totals[0] += rs.getLong("total");
                    totals[1] += rs.getLong("held");
                });
        return new Counts(totals[0], totals[1], byType);
    }

    @Override
    public List<LogRow> log(UUID reportedUserId, Instant from, Instant to) {
        return jdbc.query("""
                select l.created_at, l.target_type, l.target_id, l.reported_username, m.username as moderator, l.action,
                       l.reason, l.note, l.content_sha256
                  from moderation_log l
                  left join users m on m.id = l.moderator_id
                 where l.created_at >= :from and l.created_at < :to
                   and (cast(:user as uuid) is null or l.reported_user_id = :user)
                 order by l.created_at, l.id""",
                new MapSqlParameterSource("from", Timestamp.from(from)).addValue("to", Timestamp.from(to))
                        .addValue("user", reportedUserId, Types.OTHER),
                (rs, n) -> new LogRow(rs.getTimestamp("created_at").toInstant(), rs.getString("target_type"),
                        rs.getObject("target_id", UUID.class), rs.getString("reported_username"), rs.getString("moderator"),
                        rs.getString("action"), rs.getString("reason"), rs.getString("note"), rs.getString("content_sha256")));
    }
}
