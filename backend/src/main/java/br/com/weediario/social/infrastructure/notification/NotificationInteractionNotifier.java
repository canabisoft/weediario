package br.com.weediario.social.infrastructure.notification;

import br.com.weediario.notification.application.usecase.NotifyUseCase;
import br.com.weediario.social.application.port.InteractionNotifier;
import br.com.weediario.social.domain.model.CommentableDiary;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Tradução das interações sociais para o contexto de notificações. */
@Component
public class NotificationInteractionNotifier implements InteractionNotifier {

    private final NotifyUseCase notify;

    public NotificationInteractionNotifier(NotifyUseCase notify) {
        this.notify = notify;
    }

    @Override
    public void commentPosted(CommentableDiary diary, UUID commentAuthorId) {
        notify.commentPosted(diary.diaryId(), diary.ownerId(), commentAuthorId);
    }

    @Override
    public void reactionAdded(CommentableDiary diary, UUID reactorId) {
        notify.reactionAdded(diary.diaryId(), diary.ownerId(), reactorId);
    }

    @Override
    public void followed(UUID followeeId, UUID followerId) {
        notify.followed(followeeId, followerId);
    }
}
