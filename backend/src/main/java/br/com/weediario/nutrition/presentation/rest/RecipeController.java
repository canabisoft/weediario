package br.com.weediario.nutrition.presentation.rest;

import br.com.weediario.nutrition.application.query.RecipeReadModel.RecipeFilter;
import br.com.weediario.nutrition.application.query.RecipeReadModel.RecipeSort;
import br.com.weediario.nutrition.application.query.RecipeReadModel.RecipeSummary;
import br.com.weediario.nutrition.application.query.RecipeReadModel.RecipeView;
import br.com.weediario.nutrition.application.usecase.BrowseRecipesUseCase;
import br.com.weediario.nutrition.application.usecase.ManageRecipesUseCase;
import br.com.weediario.nutrition.application.usecase.ManageRecipesUseCase.Command;
import br.com.weediario.nutrition.application.usecase.ManageRecipesUseCase.IngredientInput;
import br.com.weediario.nutrition.application.usecase.ManageRecipesUseCase.Viewed;
import br.com.weediario.nutrition.domain.model.Ingredient;
import br.com.weediario.nutrition.domain.model.IngredientUnit;
import br.com.weediario.nutrition.domain.model.RecipeApplication;
import br.com.weediario.nutrition.domain.model.RecipeDetails;
import br.com.weediario.nutrition.domain.model.RecipeKind;
import br.com.weediario.nutrition.domain.model.RecipeStage;
import br.com.weediario.nutrition.domain.model.RecipeVisibility;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.presentation.Viewer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
public class RecipeController {

    private final ManageRecipesUseCase manage;
    private final BrowseRecipesUseCase browse;

    public RecipeController(ManageRecipesUseCase manage, BrowseRecipesUseCase browse) {
        this.manage = manage;
        this.browse = browse;
    }

    /** Receitas públicas da comunidade. {@code productId}: só as que usam aquele produto do catálogo. */
    @GetMapping("/api/v1/recipes")
    public PageResult<RecipeSummary> explore(@RequestParam(required = false) RecipeKind kind,
                                             @RequestParam(required = false) @Size(max = 80) String q,
                                             @RequestParam(required = false) UUID productId,
                                             @RequestParam(required = false) RecipeSort sort,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return browse.explore(new RecipeFilter(kind, q, productId, sort), new PageQuery(page, size));
    }

    @GetMapping("/api/v1/recipes/ingredients/suggest")
    public List<String> suggestIngredients(@RequestParam @Size(max = 80) String q) {
        return browse.suggestIngredients(q);
    }

    @GetMapping("/api/v1/recipes/{id}")
    public Viewed get(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id) {
        return manage.get(id, Viewer.idOf(principal));
    }

    @PostMapping("/api/v1/recipes")
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeView create(@AuthenticationPrincipal Jwt principal, @Valid @RequestBody RecipeRequest request) {
        return manage.create(Viewer.requireId(principal), request.toCommand());
    }

    @PutMapping("/api/v1/recipes/{id}")
    public RecipeView update(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id,
                             @Valid @RequestBody RecipeRequest request) {
        return manage.update(id, Viewer.requireId(principal), request.toCommand());
    }

    @DeleteMapping("/api/v1/recipes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id) {
        manage.delete(id, Viewer.requireId(principal));
    }

    /** Copia a receita pública para as receitas de quem pediu (a cópia nasce privada). */
    @PostMapping("/api/v1/recipes/{id}/replicate")
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeView replicate(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id) {
        return manage.replicate(id, Viewer.requireId(principal));
    }

    @GetMapping("/api/v1/me/recipes")
    public PageResult<RecipeSummary> mine(@AuthenticationPrincipal Jwt principal,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return browse.mine(Viewer.requireId(principal), new PageQuery(page, size));
    }

    @GetMapping("/api/v1/growers/{username}/recipes")
    public PageResult<RecipeSummary> ofGrower(@PathVariable @Size(max = 30) String username,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return browse.ofGrower(username, new PageQuery(page, size));
    }

    public record RecipeRequest(
            @NotBlank @Size(max = RecipeDetails.NAME_MAX_LENGTH) String name,
            @NotNull RecipeKind kind,
            @NotNull RecipeApplication application,
            RecipeStage stage,
            @NotNull BigDecimal batchLiters,
            BigDecimal targetEc,
            BigDecimal targetPh,
            @Size(max = RecipeDetails.NOTES_MAX_LENGTH) String notes,
            RecipeVisibility visibility,
            @NotNull @Size(min = 1, max = RecipeDetails.MAX_INGREDIENTS) List<@Valid @NotNull IngredientRequest> ingredients) {

        Command toCommand() {
            return new Command(name, kind, application, stage, batchLiters, targetEc, targetPh, notes, visibility,
                    ingredients.stream().map(IngredientRequest::toInput).toList());
        }
    }

    /** Produto de marca: {@code productId}. Sal/insumo genérico: {@code name} e, se quiser, {@code brand}. */
    public record IngredientRequest(
            UUID productId,
            @Size(max = Ingredient.NAME_MAX_LENGTH) String name,
            @Size(max = Ingredient.NAME_MAX_LENGTH) String brand,
            @NotNull BigDecimal quantity,
            @NotNull IngredientUnit unit) {

        IngredientInput toInput() {
            return new IngredientInput(productId, name, brand, quantity, unit);
        }
    }
}
