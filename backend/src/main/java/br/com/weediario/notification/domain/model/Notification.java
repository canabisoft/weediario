package br.com.weediario.notification.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Aviso para um grower sobre uma interação com algo dele. Só referencia ids (diário, autor de uma ação pública);
 * o texto é montado na leitura, respeitando a visibilidade atual do diário.
 *
 * @param diaryId diário envolvido (vazio para novos seguidores)
 * @param actorId quem fez a ação; sempre vazio nos tipos anônimos
 */
public record Notification(UUID id, UUID recipientId, NotificationType type, UUID diaryId, UUID actorId,
                           Instant createdAt) {

    public Notification {
        Guard.notNull(id, "Id");
        Guard.notNull(recipientId, "Destinatário");
        Guard.notNull(type, "Tipo");
        Guard.notNull(createdAt, "Data");
        if (type.anonymous() && actorId != null) {
            throw new DomainException("Notificação anônima não pode guardar quem fez a ação");
        }
    }

    /** Ninguém é notificado das próprias ações. */
    public static Optional<Notification> comment(UUID diaryOwnerId, UUID diaryId, UUID commentAuthorId, Clock clock) {
        return diaryOwnerId.equals(commentAuthorId) ? Optional.empty()
                : Optional.of(create(diaryOwnerId, NotificationType.COMMENT, diaryId, commentAuthorId, clock));
    }

    /** Recebe quem reagiu só para descartar a reação do próprio dono; não é guardado. */
    public static Optional<Notification> reaction(UUID diaryOwnerId, UUID diaryId, UUID reactorId, Clock clock) {
        return diaryOwnerId.equals(reactorId) ? Optional.empty()
                : Optional.of(create(diaryOwnerId, NotificationType.REACTION, diaryId, null, clock));
    }

    public static Optional<Notification> newFollower(UUID followeeId, UUID followerId, Clock clock) {
        return followeeId.equals(followerId) ? Optional.empty()
                : Optional.of(create(followeeId, NotificationType.NEW_FOLLOWER, null, null, clock));
    }

    private static Notification create(UUID recipientId, NotificationType type, UUID diaryId, UUID actorId, Clock clock) {
        return new Notification(UUID.randomUUID(), recipientId, type, diaryId, actorId, clock.instant());
    }
}
