package br.com.weediario.nutrition.domain.repository;

import br.com.weediario.nutrition.domain.model.Recipe;
import br.com.weediario.nutrition.domain.model.RecipeId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeRepository {

    Recipe save(Recipe recipe);

    Optional<Recipe> findById(RecipeId id);

    List<Recipe> findAllById(Collection<RecipeId> ids);

    void delete(RecipeId id);

    long countByOwner(UUID ownerId);

    /** Receitas usadas num cultivo, na ordem em que foram vinculadas. */
    List<RecipeId> usedInDiary(UUID diaryId);

    void replaceDiaryRecipes(UUID diaryId, List<RecipeId> recipeIds);
}
