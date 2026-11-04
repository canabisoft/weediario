package br.com.weediario.moderation.infrastructure.notification;

import br.com.weediario.moderation.application.port.HoldNotifier;
import br.com.weediario.moderation.application.port.ModeratedContent.ContentInfo;
import br.com.weediario.notification.application.usecase.NotifyUseCase;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Aviso ao autor pelo contexto de notificações. O link do conteúdo (ContentInfo#linkId) é o diário nos diários e nos
 * comentários deles, a receita nas receitas e nos comentários delas; review e perfil não têm página própria.
 */
@Component
public class NotificationHoldNotifier implements HoldNotifier {

    private final NotifyUseCase notify;

    public NotificationHoldNotifier(NotifyUseCase notify) {
        this.notify = notify;
    }

    @Override
    public void contentHeld(ContentInfo held) {
        if (held.authorId() == null) {
            return;
        }
        UUID diaryId = null;
        UUID recipeId = null;
        switch (held.ref().type()) {
            case DIARY, COMMENT -> diaryId = idOrNull(held.linkId());
            case RECIPE, RECIPE_COMMENT -> recipeId = idOrNull(held.linkId());
            default -> {
                // Review e perfil: o aviso não leva a uma página.
            }
        }
        notify.contentHeld(held.authorId(), held.ref().type().name(), held.ref().id(), diaryId, recipeId);
    }

    private static UUID idOrNull(String linkId) {
        try {
            return linkId == null ? null : UUID.fromString(linkId);
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }
}
