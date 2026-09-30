package br.com.weediario.notification.application.usecase;

import br.com.weediario.notification.application.port.NotificationPush;
import br.com.weediario.notification.domain.model.Notification;
import br.com.weediario.notification.domain.repository.NotificationRepository;
import br.com.weediario.shared.application.UnitOfWork;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cria notificações a partir das interações vindas dos outros contextos. Roda dentro da transação de quem
 * chamou: se a interação for desfeita, a notificação também é.
 */
public final class NotifyUseCase {

    /** Notificações não são histórico: somem depois deste prazo, lidas ou não. */
    public static final Duration RETENTION = Duration.ofDays(90);

    private final NotificationRepository notifications;
    private final NotificationPush push;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public NotifyUseCase(NotificationRepository notifications, NotificationPush push, UnitOfWork unitOfWork, Clock clock) {
        this.notifications = notifications;
        this.push = push;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public void commentPosted(UUID diaryId, UUID diaryOwnerId, UUID commentAuthorId) {
        deliver(Notification.comment(diaryOwnerId, diaryId, commentAuthorId, clock));
    }

    public void reactionAdded(UUID diaryId, UUID diaryOwnerId, UUID reactorId) {
        deliver(Notification.reaction(diaryOwnerId, diaryId, reactorId, clock));
    }

    public void followed(UUID followeeId, UUID followerId) {
        deliver(Notification.newFollower(followeeId, followerId, clock));
    }

    /** Só updates de diários públicos chegam aos seguidores. */
    public void updatePublished(UUID diaryId, UUID ownerId, boolean publicDiary) {
        if (!publicDiary) {
            return;
        }
        unitOfWork.run(() -> {
            List<UUID> recipients = notifications.addFollowedUpdate(ownerId, diaryId, clock.instant());
            push.unreadChanged(recipients);
        });
    }

    public int purgeExpired() {
        return unitOfWork.execute(() -> notifications.deleteCreatedBefore(clock.instant().minus(RETENTION)));
    }

    private void deliver(Optional<Notification> notification) {
        notification.ifPresent(n -> unitOfWork.run(() -> {
            notifications.add(n);
            push.unreadChanged(List.of(n.recipientId()));
        }));
    }
}
