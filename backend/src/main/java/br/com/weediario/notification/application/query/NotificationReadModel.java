package br.com.weediario.notification.application.query;

import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;

import java.time.Instant;
import java.util.UUID;

/**
 * Caixa de notificações. Some da lista (e do contador) o que o destinatário não pode mais ver:
 * diário que ficou privado, autor suspenso.
 */
public interface NotificationReadModel {

    PageResult<NotificationView> inbox(UUID recipientId, PageQuery page);

    long unreadCount(UUID recipientId);

    /**
     * @param recipeId    receita envolvida (respostas a comentários de receita)
     * @param actor       apelido de quem fez a ação (nulo nos tipos anônimos)
     * @param occurrences quantas vezes aconteceu enquanto não lida (reações, seguidores, updates)
     */
    record NotificationView(UUID id, String type, UUID diaryId, String diaryTitle, UUID recipeId, String recipeName,
                            String actor, int occurrences, Instant createdAt, boolean read) {
    }
}
