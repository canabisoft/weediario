package br.com.weediario.social.application.port;

import br.com.weediario.social.domain.model.CommentableDiary;

import java.util.UUID;

/**
 * Avisa o dono do conteúdo sobre interações. Quem reagiu ou seguiu é passado só para evitar notificar
 * a pessoa das próprias ações; a notificação desses tipos é anônima.
 */
public interface InteractionNotifier {

    void commentPosted(CommentableDiary diary, UUID commentAuthorId);

    void reactionAdded(CommentableDiary diary, UUID reactorId);

    void followed(UUID followeeId, UUID followerId);
}
