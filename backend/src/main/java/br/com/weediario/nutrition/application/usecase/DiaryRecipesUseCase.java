package br.com.weediario.nutrition.application.usecase;

import br.com.weediario.nutrition.application.port.GrowDiaries;
import br.com.weediario.nutrition.application.port.GrowDiaries.DiaryRef;
import br.com.weediario.nutrition.application.query.RecipeReadModel;
import br.com.weediario.nutrition.application.query.RecipeReadModel.RecipeSummary;
import br.com.weediario.nutrition.domain.model.Recipe;
import br.com.weediario.nutrition.domain.model.RecipeId;
import br.com.weediario.nutrition.domain.model.RecipeVisibility;
import br.com.weediario.nutrition.domain.repository.RecipeRepository;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Receitas que o grower está usando num cultivo (ex.: a solução mineral da vega e a da flora).
 * <p>
 * Quem vê o diário vê as receitas públicas vinculadas; as privadas só aparecem para o autor.
 */
public final class DiaryRecipesUseCase {

    public static final int MAX_PER_DIARY = 5;

    private final GrowDiaries diaries;
    private final RecipeRepository recipes;
    private final RecipeReadModel readModel;
    private final UnitOfWork unitOfWork;

    public DiaryRecipesUseCase(GrowDiaries diaries, RecipeRepository recipes, RecipeReadModel readModel,
                               UnitOfWork unitOfWork) {
        this.diaries = diaries;
        this.recipes = recipes;
        this.readModel = readModel;
        this.unitOfWork = unitOfWork;
    }

    public List<RecipeSummary> list(UUID diaryId, Optional<UUID> viewerId) {
        return unitOfWork.execute(() -> {
            DiaryRef diary = visibleDiary(diaryId, viewerId);
            boolean owner = viewerId.map(diary.ownerId()::equals).orElse(false);
            List<UUID> ids = recipes.usedInDiary(diaryId).stream().map(RecipeId::value).toList();
            return readModel.summaries(ids).stream()
                    .filter(recipe -> owner || recipe.visibility() == RecipeVisibility.PUBLIC)
                    .toList();
        });
    }

    /**
     * Substitui as receitas do cultivo. Valem as próprias (públicas ou privadas) e as públicas de outros growers;
     * para ajustar uma receita alheia, o caminho é replicar antes.
     */
    public List<RecipeSummary> replace(UUID diaryId, UUID ownerId, List<UUID> recipeIds) {
        List<RecipeId> ids = new LinkedHashSet<>(recipeIds == null ? List.<UUID>of() : recipeIds).stream()
                .map(RecipeId::new).toList();
        if (ids.size() > MAX_PER_DIARY) {
            throw new DomainException("Máximo de " + MAX_PER_DIARY + " receitas por cultivo");
        }
        unitOfWork.run(() -> {
            DiaryRef diary = diaries.find(diaryId)
                    .filter(found -> found.ownerId().equals(ownerId))
                    .orElseThrow(() -> new ResourceNotFoundException("Diário"));
            List<Recipe> found = recipes.findAllById(ids);
            if (found.size() != ids.size() || !found.stream().allMatch(recipe -> recipe.isVisibleTo(Optional.of(ownerId)))) {
                throw new ResourceNotFoundException("Receita");
            }
            recipes.replaceDiaryRecipes(diary.id(), ids);
        });
        return list(diaryId, Optional.of(ownerId));
    }

    private DiaryRef visibleDiary(UUID diaryId, Optional<UUID> viewerId) {
        return diaries.find(diaryId)
                .filter(diary -> diary.isPublic() || viewerId.map(diary.ownerId()::equals).orElse(false))
                .orElseThrow(() -> new ResourceNotFoundException("Diário"));
    }
}
