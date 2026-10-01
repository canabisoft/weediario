package br.com.weediario.nutrition.infrastructure.moderation;

import br.com.weediario.moderation.application.usecase.ContentHoldUseCase;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.TargetType;
import br.com.weediario.nutrition.application.port.RecipeModeration;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ModerationRecipeModeration implements RecipeModeration {

    private final ContentHoldUseCase holds;

    public ModerationRecipeModeration(ContentHoldUseCase holds) {
        this.holds = holds;
    }

    @Override
    public void recipeHeld(UUID recipeId, String reason) {
        holds.heldByFilter(TargetType.RECIPE, recipeId, reason);
    }

    @Override
    public void commentHeld(UUID commentId, String reason) {
        holds.heldByFilter(TargetType.RECIPE_COMMENT, commentId, reason);
    }
}
