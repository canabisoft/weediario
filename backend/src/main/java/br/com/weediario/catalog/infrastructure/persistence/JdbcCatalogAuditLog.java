package br.com.weediario.catalog.infrastructure.persistence;

import br.com.weediario.catalog.domain.repository.CatalogAuditLog;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;

@Repository
public class JdbcCatalogAuditLog implements CatalogAuditLog {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCatalogAuditLog(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(Entry entry) {
        jdbc.update("""
                insert into catalog_audit (id, action, item_kind, item_id, item_name, related_id, moderator_id, created_at)
                values (:id, :action, :kind, :item, :name, :related, :moderator, :at)""",
                new MapSqlParameterSource("id", UUID.randomUUID())
                        .addValue("action", entry.action().name())
                        .addValue("kind", entry.kind().name())
                        .addValue("item", entry.itemId())
                        .addValue("name", entry.itemName() == null ? null
                                : entry.itemName().substring(0, Math.min(120, entry.itemName().length())))
                        .addValue("related", entry.relatedId(), Types.OTHER)
                        .addValue("moderator", entry.moderatorId(), Types.OTHER)
                        .addValue("at", Timestamp.from(entry.at())));
    }

    @Override
    public void deleteBefore(Instant cutoff) {
        jdbc.update("delete from catalog_audit where created_at < :cutoff",
                new MapSqlParameterSource("cutoff", Timestamp.from(cutoff)));
    }
}
