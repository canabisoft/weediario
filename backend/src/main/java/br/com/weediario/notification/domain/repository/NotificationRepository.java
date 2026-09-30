package br.com.weediario.notification.domain.repository;

import br.com.weediario.notification.domain.model.Notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository {

    /** Guarda a notificação; nos tipos acumulados, soma na notificação ainda não lida, se houver. */
    void add(Notification notification);

    /**
     * Avisa os seguidores de um grower que ele publicou um update (acumulando por diário enquanto não lido).
     *
     * @return quem foi avisado
     */
    List<UUID> addFollowedUpdate(UUID growerId, UUID diaryId, Instant at);

    void markAllRead(UUID recipientId, Instant at);

    void markRead(UUID recipientId, UUID notificationId, Instant at);

    /** Retenção: apaga o que foi criado antes do instante informado. */
    int deleteCreatedBefore(Instant cutoff);
}
