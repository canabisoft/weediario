package br.com.weediario.moderation.infrastructure.persistence;

import br.com.weediario.moderation.domain.model.ContentRef;
import br.com.weediario.moderation.domain.model.ModerationLogEntry;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.HoldCause;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.ReportReason;
import br.com.weediario.moderation.domain.repository.ModerationRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;

@Repository
public class JdbcModerationRepository implements ModerationRepository {

    /**
     * Conteúdo que ainda existe, por tipo (tabela e coluna fixas, nunca texto do usuário). Usado na limpeza de
     * denúncias e retenções órfãs.
     */
    static final String EXISTS = """
            case target_type
                when 'COMMENT' then exists (select 1 from diary_comments x where x.id = target_id)
                when 'DIARY' then exists (select 1 from grow_diaries x where x.id = target_id)
                when 'REVIEW' then exists (select 1 from reviews x where x.id = target_id)
                when 'RECIPE' then exists (select 1 from recipes x where x.id = target_id)
                when 'RECIPE_COMMENT' then exists (select 1 from recipe_comments x where x.id = target_id)
                when 'PROFILE' then exists (select 1 from users x where x.id = target_id)
                else false
            end""";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcModerationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long addReport(ContentRef target, String reporterPseudonym, ReportReason reason, Instant at) {
        MapSqlParameterSource params = ref(target).addValue("reporter", reporterPseudonym)
                .addValue("reason", reason.name()).addValue("at", Timestamp.from(at));
        jdbc.update("""
                insert into content_reports (target_type, target_id, reporter, reason, created_at)
                values (:type, :id, :reporter, :reason, :at)
                on conflict do nothing""", params);
        Long total = jdbc.queryForObject(
                "select count(*) from content_reports where target_type = :type and target_id = :id", params, Long.class);
        return total == null ? 0 : total;
    }

    @Override
    public void clearReports(ContentRef target) {
        jdbc.update("delete from content_reports where target_type = :type and target_id = :id", ref(target));
    }

    @Override
    public boolean isHeld(ContentRef target) {
        Boolean held = jdbc.queryForObject(
                "select exists (select 1 from moderation_holds where target_type = :type and target_id = :id)",
                ref(target), Boolean.class);
        return Boolean.TRUE.equals(held);
    }

    @Override
    public void hold(ContentRef target, HoldCause cause, String detail, Instant at) {
        jdbc.update("""
                insert into moderation_holds (target_type, target_id, cause, detail, held_at)
                values (:type, :id, :cause, :detail, :at)
                on conflict (target_type, target_id) do nothing""",
                ref(target).addValue("cause", cause.name()).addValue("detail", detail, Types.VARCHAR)
                        .addValue("at", Timestamp.from(at)));
    }

    @Override
    public void release(ContentRef target) {
        jdbc.update("delete from moderation_holds where target_type = :type and target_id = :id", ref(target));
    }

    @Override
    public void log(ModerationLogEntry entry) {
        jdbc.update("""
                insert into moderation_log (id, target_type, target_id, reported_user_id, reported_username, moderator_id,
                                            action, reason, note, content_sha256, created_at)
                values (:id, :type, :targetId, :reported, :username, :moderator, :action, :reason, :note, :hash, :at)""",
                new MapSqlParameterSource("id", entry.id())
                        .addValue("type", entry.target().type().name())
                        .addValue("targetId", entry.target().id())
                        .addValue("reported", entry.reportedUserId(), Types.OTHER)
                        .addValue("username", entry.reportedUsername(), Types.VARCHAR)
                        .addValue("moderator", entry.moderatorId(), Types.OTHER)
                        .addValue("action", entry.action().name())
                        .addValue("reason", entry.reason().name())
                        .addValue("note", entry.note(), Types.VARCHAR)
                        .addValue("hash", entry.contentSha256(), Types.VARCHAR)
                        .addValue("at", Timestamp.from(entry.createdAt())));
    }

    @Override
    public int deleteLogBefore(Instant cutoff) {
        return jdbc.update("delete from moderation_log where created_at < :cutoff",
                new MapSqlParameterSource("cutoff", Timestamp.from(cutoff)));
    }

    @Override
    public int deleteOrphans() {
        return jdbc.update("delete from content_reports where not (" + EXISTS + ")", new MapSqlParameterSource())
                + jdbc.update("delete from moderation_holds where not (" + EXISTS + ")", new MapSqlParameterSource());
    }

    private static MapSqlParameterSource ref(ContentRef target) {
        return new MapSqlParameterSource("type", target.type().name()).addValue("id", target.id());
    }
}
