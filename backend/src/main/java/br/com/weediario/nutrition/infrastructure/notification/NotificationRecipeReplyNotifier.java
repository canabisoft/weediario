package br.com.weediario.nutrition.infrastructure.notification;

import br.com.weediario.notification.application.usecase.NotifyUseCase;
import br.com.weediario.nutrition.application.port.RecipeReplyNotifier;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class NotificationRecipeReplyNotifier implements RecipeReplyNotifier {

    private final NotifyUseCase notify;

    public NotificationRecipeReplyNotifier(NotifyUseCase notify) {
        this.notify = notify;
    }

    @Override
    public void replied(UUID recipeId, UUID answeredAuthorId, UUID replierId) {
        notify.recipeCommentReplied(recipeId, answeredAuthorId, replierId);
    }
}
