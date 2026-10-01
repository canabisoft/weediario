package br.com.weediario.moderation.infrastructure.query;

import br.com.weediario.moderation.application.query.PlatformMetrics;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

/** Contagens agregadas em SQL; nenhuma linha por pessoa sai daqui. */
@Component
public class JdbcPlatformMetrics implements PlatformMetrics {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPlatformMetrics(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Accounts accounts() {
        return jdbc.queryForObject("""
                select count(*) as total,
                       count(*) filter (where status = 'ACTIVE') as active,
                       count(*) filter (where status = 'SUSPENDED') as suspended,
                       count(*) filter (where status = 'BANNED') as banned,
                       count(*) filter (where role = 'MODERATOR') as moderators,
                       count(*) filter (where role = 'ADMIN') as admins
                  from users""", new MapSqlParameterSource(),
                (rs, n) -> new Accounts(rs.getLong("total"), rs.getLong("active"), rs.getLong("suspended"),
                        rs.getLong("banned"), rs.getLong("moderators"), rs.getLong("admins")));
    }

    @Override
    public Content content() {
        return jdbc.queryForObject("""
                select (select count(*) from grow_diaries where visibility = 'PUBLIC' and not moderation_hold) as public_diaries,
                       (select count(*) from grow_diaries where visibility = 'PRIVATE') as private_diaries,
                       (select count(*) from diary_updates u join grow_diaries d on d.id = u.diary_id
                         where d.visibility = 'PUBLIC' and not d.moderation_hold) as diary_updates,
                       (select count(*) from diary_comments where not hidden) as comments,
                       (select count(*) from recipes where visibility = 'PUBLIC' and not moderation_hold) as public_recipes,
                       (select count(*) from recipe_comments where not hidden) as recipe_comments,
                       (select count(*) from reviews where not hidden) as reviews""", new MapSqlParameterSource(),
                (rs, n) -> new Content(rs.getLong("public_diaries"), rs.getLong("private_diaries"),
                        rs.getLong("diary_updates"), rs.getLong("comments"), rs.getLong("public_recipes"),
                        rs.getLong("recipe_comments"), rs.getLong("reviews")));
    }

    @Override
    public Activity activitySince(Instant since) {
        return jdbc.queryForObject("""
                select (select count(*) from users where created_at >= :since) as new_accounts,
                       (select count(*) from grow_diaries where created_at >= :since) as new_diaries,
                       (select count(*) from diary_updates where created_at >= :since) as diary_updates,
                       (select count(*) from diary_comments where created_at >= :since) as comments,
                       (select count(*) from reviews where created_at >= :since) as reviews,
                       (select count(*) from recipes where created_at >= :since) as recipes,
                       (select count(distinct author) from (
                            select owner_id as author from grow_diaries where created_at >= :since
                            union all
                            select d.owner_id from diary_updates u join grow_diaries d on d.id = u.diary_id
                             where u.created_at >= :since
                            union all
                            select author_id from diary_comments where created_at >= :since
                            union all
                            select author_id from reviews where created_at >= :since
                            union all
                            select owner_id from recipes where created_at >= :since) authors) as active_growers""",
                new MapSqlParameterSource("since", Timestamp.from(since)),
                (rs, n) -> new Activity(rs.getLong("new_accounts"), rs.getLong("new_diaries"),
                        rs.getLong("diary_updates"), rs.getLong("comments"), rs.getLong("reviews"),
                        rs.getLong("recipes"), rs.getLong("active_growers")));
    }

    @Override
    public long moderationDecisionsSince(Instant since) {
        Long count = jdbc.queryForObject("select count(*) from moderation_log where created_at >= :since",
                new MapSqlParameterSource("since", Timestamp.from(since)), Long.class);
        return count == null ? 0 : count;
    }
}
