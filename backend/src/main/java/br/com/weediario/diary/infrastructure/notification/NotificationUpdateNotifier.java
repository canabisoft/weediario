package br.com.weediario.diary.infrastructure.notification;

import br.com.weediario.diary.application.port.UpdateNotifier;
import br.com.weediario.notification.application.usecase.NotifyUseCase;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class NotificationUpdateNotifier implements UpdateNotifier {

    private final NotifyUseCase notify;

    public NotificationUpdateNotifier(NotifyUseCase notify) {
        this.notify = notify;
    }

    @Override
    public void updatePublished(UUID diaryId, UUID ownerId, boolean publicDiary) {
        notify.updatePublished(diaryId, ownerId, publicDiary);
    }
}
