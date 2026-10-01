package br.com.weediario.review.infrastructure.moderation;

import br.com.weediario.moderation.application.usecase.ContentHoldUseCase;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.TargetType;
import br.com.weediario.review.application.port.ReviewModeration;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ModerationReviewModeration implements ReviewModeration {

    private final ContentHoldUseCase holds;

    public ModerationReviewModeration(ContentHoldUseCase holds) {
        this.holds = holds;
    }

    @Override
    public void heldByFilter(UUID reviewId, String reason) {
        holds.heldByFilter(TargetType.REVIEW, reviewId, reason);
    }
}
