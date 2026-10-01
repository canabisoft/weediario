package br.com.weediario.nutrition.application.usecase;

import br.com.weediario.nutrition.application.query.RecipeReadModel;
import br.com.weediario.nutrition.application.query.RecipeReadModel.RecipeFilter;
import br.com.weediario.nutrition.application.query.RecipeReadModel.RecipeSummary;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;

import java.util.List;
import java.util.UUID;

public final class BrowseRecipesUseCase {

    public static final int SUGGESTIONS = 8;

    private final RecipeReadModel readModel;

    public BrowseRecipesUseCase(RecipeReadModel readModel) {
        this.readModel = readModel;
    }

    public PageResult<RecipeSummary> explore(RecipeFilter filter, PageQuery page) {
        return readModel.publicRecipes(filter, page);
    }

    public PageResult<RecipeSummary> mine(UUID ownerId, PageQuery page) {
        return readModel.ofOwner(ownerId, page);
    }

    /** Perfil público: só as receitas que o grower escolheu deixar públicas. */
    public PageResult<RecipeSummary> ofGrower(String username, PageQuery page) {
        return readModel.publicOfGrower(username, page);
    }

    public List<String> suggestIngredients(String query) {
        return query == null || query.strip().length() < 2 ? List.of()
                : readModel.suggestIngredientNames(query.strip(), SUGGESTIONS);
    }
}
