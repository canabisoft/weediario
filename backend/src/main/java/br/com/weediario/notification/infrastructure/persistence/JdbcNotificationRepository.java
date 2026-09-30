package br.com.weediario.notification.infrastructure.persistence;

import br.com.weediario.notification.domain.model.Notification;
import br.com.weediario.notification.domain.repository.NotificationRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcNotificationRepository implements NotificationRepository {

    /** Alvo do índice único parcial: uma notificação não lida por destinatário, tipo e diário. */
    private static final String ACCUMULATE = """
            on conflict (recipient_id, type, coalesce(diary_id, '00000000-0000-0000-0000-000000000000'::uuid))
               where read_at is null and type in ('REACTION', 'NEW_FOLLOWER', 'FOLLOWED_UPDATE')
            do update set occurrences = notifications.occurrences + 1, created_at = excluded.created_at""";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcNotificationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(Notification notification) {
        jdbc.update("""
                insert into notifications (id, recipient_id, type, diary_id, actor_id, occurrences, created_at)
                values (:id, :recipient, :type, :diary, :actor, 1, :at)
                """ + (notification.type().aggregated() ? ACCUMULATE : ""),
                new MapSqlParameterSource("id", notification.id())
                        .addValue("recipient", notification.recipientId())
                        .addValue("type", notification.type().name())
                        .addValue("diary", notification.diaryId(), Types.OTHER)
                        .addValue("actor", notification.actorId(), Types.OTHER)
                        .addValue("at", Timestamp.from(notification.createdAt())));
    }

    @Override
    public List<UUID> addFollowedUpdate(UUID growerId, UUID diaryId, Instant at) {
        return jdbc.queryForList("""
                insert into notifications (id, recipient_id, type, diary_id, actor_id, occurrences, created_at)
                select gen_random_uuid(), f.follower_id, 'FOLLOWED_UPDATE', :diary, :grower, 1, :at
                  from grower_follows f
                  join users u on u.id = f.follower_id and u.status = 'ACTIVE'
                 where f.followee_id = :grower
                """ + ACCUMULATE + " returning recipient_id",
                new MapSqlParameterSource("grower", growerId).addValue("diary", diaryId)
                        .addValue("at", Timestamp.from(at)),
                UUID.class);
    }

    @Override
    public void markAllRead(UUID recipientId, Instant at) {
        jdbc.update("update notifications set read_at = :at where recipient_id = :recipient and read_at is null",
                new MapSqlParameterSource("recipient", recipientId).addValue("at", Timestamp.from(at)));
    }

    @Override
    public void markRead(UUID recipientId, UUID notificationId, Instant at) {
        jdbc.update("""
                update notifications set read_at = :at
                 where id = :id and recipient_id = :recipient and read_at is null""",
                new MapSqlParameterSource("recipient", recipientId).addValue("id", notificationId)
                        .addValue("at", Timestamp.from(at)));
    }

    @Override
    public int deleteCreatedBefore(Instant cutoff) {
        return jdbc.update("delete from notifications where created_at < :cutoff",
                new MapSqlParameterSource("cutoff", Timestamp.from(cutoff)));
    }
}
