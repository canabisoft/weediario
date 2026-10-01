package br.com.weediario.social.infrastructure.moderation;

import br.com.weediario.moderation.application.usecase.ContentHoldUseCase;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.TargetType;
import br.com.weediario.social.application.port.CommentModeration;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ModerationCommentModeration implements CommentModeration {

    private final ContentHoldUseCase holds;

    public ModerationCommentModeration(ContentHoldUseCase holds) {
        this.holds = holds;
    }

    @Override
    public void heldByFilter(UUID commentId, String reason) {
        holds.heldByFilter(TargetType.COMMENT, commentId, reason);
    }
}
