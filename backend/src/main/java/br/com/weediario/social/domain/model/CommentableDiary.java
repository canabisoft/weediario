package br.com.weediario.social.domain.model;

import java.util.Optional;
import java.util.UUID;

/**
 * O que a interação social precisa saber do diário (traduzido do contexto de diários): de quem é e quem pode vê-lo.
 */
public record CommentableDiary(UUID diaryId, UUID ownerId, boolean publiclyVisible, boolean communityVisible) {

    public boolean isVisibleTo(Optional<UUID> viewerId) {
        return publiclyVisible
                || (communityVisible && viewerId.isPresent())
                || viewerId.map(ownerId::equals).orElse(false);
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }
}
