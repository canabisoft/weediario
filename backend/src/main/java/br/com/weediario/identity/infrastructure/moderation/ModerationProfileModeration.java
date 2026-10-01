package br.com.weediario.identity.infrastructure.moderation;

import br.com.weediario.identity.application.port.ProfileModeration;
import br.com.weediario.moderation.application.usecase.ContentHoldUseCase;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.TargetType;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ModerationProfileModeration implements ProfileModeration {

    private final ContentHoldUseCase holds;

    public ModerationProfileModeration(ContentHoldUseCase holds) {
        this.holds = holds;
    }

    @Override
    public void heldByFilter(UUID userId, String reason) {
        holds.heldByFilter(TargetType.PROFILE, userId, reason);
    }
}
