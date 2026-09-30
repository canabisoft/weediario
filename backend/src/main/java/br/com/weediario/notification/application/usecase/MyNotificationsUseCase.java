package br.com.weediario.notification.application.usecase;

import br.com.weediario.notification.application.port.NotificationPush;
import br.com.weediario.notification.application.query.NotificationReadModel;
import br.com.weediario.notification.application.query.NotificationReadModel.NotificationView;
import br.com.weediario.notification.domain.repository.NotificationRepository;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.application.UnitOfWork;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * A caixa de notificações de quem está logado. Cada pessoa só enxerga e marca as próprias.
 */
public final class MyNotificationsUseCase {

    private final NotificationReadModel readModel;
    private final NotificationRepository notifications;
    private final NotificationPush push;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public MyNotificationsUseCase(NotificationReadModel readModel, NotificationRepository notifications,
                                  NotificationPush push, UnitOfWork unitOfWork, Clock clock) {
        this.readModel = readModel;
        this.notifications = notifications;
        this.push = push;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public PageResult<NotificationView> inbox(UUID recipientId, PageQuery page) {
        return readModel.inbox(recipientId, page);
    }

    public long unreadCount(UUID recipientId) {
        return readModel.unreadCount(recipientId);
    }

    /** As outras abas/aparelhos abertos recebem o contador zerado. */
    public void markAllRead(UUID recipientId) {
        unitOfWork.run(() -> {
            notifications.markAllRead(recipientId, clock.instant());
            push.unreadChanged(List.of(recipientId));
        });
    }

    public void markRead(UUID recipientId, UUID notificationId) {
        unitOfWork.run(() -> {
            notifications.markRead(recipientId, notificationId, clock.instant());
            push.unreadChanged(List.of(recipientId));
        });
    }
}
