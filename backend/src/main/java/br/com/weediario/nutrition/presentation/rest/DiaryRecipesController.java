package br.com.weediario.nutrition.presentation.rest;

import br.com.weediario.nutrition.application.query.RecipeReadModel.RecipeSummary;
import br.com.weediario.nutrition.application.usecase.DiaryRecipesUseCase;
import br.com.weediario.shared.presentation.Viewer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Receitas de nutrição usadas num cultivo. */
@RestController
public class DiaryRecipesController {

    private final DiaryRecipesUseCase diaryRecipes;

    public DiaryRecipesController(DiaryRecipesUseCase diaryRecipes) {
        this.diaryRecipes = diaryRecipes;
    }

    @GetMapping("/api/v1/diaries/{diaryId}/recipes")
    public List<RecipeSummary> list(@AuthenticationPrincipal Jwt principal, @PathVariable UUID diaryId) {
        return diaryRecipes.list(diaryId, Viewer.idOf(principal));
    }

    @PutMapping("/api/v1/diaries/{diaryId}/recipes")
    public List<RecipeSummary> replace(@AuthenticationPrincipal Jwt principal, @PathVariable UUID diaryId,
                                       @Valid @RequestBody RecipesRequest request) {
        return diaryRecipes.replace(diaryId, Viewer.requireId(principal), request.recipeIds());
    }

    public record RecipesRequest(@NotNull @Size(max = DiaryRecipesUseCase.MAX_PER_DIARY) List<@NotNull UUID> recipeIds) {
    }
}
