package br.com.weediario.nutrition.application.usecase;

import br.com.weediario.nutrition.application.port.ProductCatalog;
import br.com.weediario.nutrition.application.port.ProductCatalog.CatalogProduct;
import br.com.weediario.nutrition.application.query.RecipeReadModel;
import br.com.weediario.nutrition.application.query.RecipeReadModel.RecipeView;
import br.com.weediario.nutrition.domain.model.Ingredient;
import br.com.weediario.nutrition.domain.model.IngredientUnit;
import br.com.weediario.nutrition.domain.model.Recipe;
import br.com.weediario.nutrition.domain.model.RecipeApplication;
import br.com.weediario.nutrition.domain.model.RecipeDetails;
import br.com.weediario.nutrition.domain.model.RecipeId;
import br.com.weediario.nutrition.domain.model.RecipeKind;
import br.com.weediario.nutrition.domain.model.RecipeStage;
import br.com.weediario.nutrition.domain.model.RecipeVisibility;
import br.com.weediario.nutrition.domain.repository.RecipeRepository;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Criar, editar, apagar, ver e replicar receitas.
 * <p>
 * Ingrediente de marca: nome e marca vêm do catálogo (o que o cliente mandar é ignorado), então a receita sempre
 * cita o produto do jeito que a comunidade o cadastrou.
 */
public final class ManageRecipesUseCase {

    public static final int MAX_RECIPES_PER_GROWER = 200;

    private final RecipeRepository recipes;
    private final RecipeReadModel readModel;
    private final ProductCatalog catalog;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ManageRecipesUseCase(RecipeRepository recipes, RecipeReadModel readModel, ProductCatalog catalog,
                                UnitOfWork unitOfWork, Clock clock) {
        this.recipes = recipes;
        this.readModel = readModel;
        this.catalog = catalog;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public RecipeView create(UUID ownerId, Command command) {
        RecipeDetails details = details(command);
        UUID id = unitOfWork.execute(() -> {
            if (recipes.countByOwner(ownerId) >= MAX_RECIPES_PER_GROWER) {
                throw new DomainException("Limite de " + MAX_RECIPES_PER_GROWER + " receitas atingido");
            }
            return recipes.save(Recipe.create(ownerId, details, command.visibility(), clock)).id().value();
        });
        return view(id);
    }

    public RecipeView update(UUID recipeId, UUID ownerId, Command command) {
        RecipeDetails details = details(command);
        unitOfWork.run(() -> {
            Recipe recipe = owned(recipeId, ownerId);
            recipe.revise(details, command.visibility(), clock);
            recipes.save(recipe);
        });
        return view(recipeId);
    }

    public void delete(UUID recipeId, UUID ownerId) {
        unitOfWork.run(() -> recipes.delete(owned(recipeId, ownerId).id()));
    }

    /** Copia uma receita pública para o perfil de quem pediu (nasce privada). */
    public RecipeView replicate(UUID recipeId, UUID growerId) {
        UUID copyId = unitOfWork.execute(() -> {
            Recipe original = recipes.findById(new RecipeId(recipeId))
                    .filter(recipe -> recipe.isVisibleTo(Optional.of(growerId)))
                    .orElseThrow(() -> new ResourceNotFoundException("Receita"));
            if (recipes.countByOwner(growerId) >= MAX_RECIPES_PER_GROWER) {
                throw new DomainException("Limite de " + MAX_RECIPES_PER_GROWER + " receitas atingido");
            }
            return recipes.save(original.replicateFor(growerId, clock)).id().value();
        });
        return view(copyId);
    }

    /** Privada responde 404 para terceiros: não revela nem que existe. */
    public Viewed get(UUID recipeId, Optional<UUID> viewerId) {
        Recipe recipe = unitOfWork.execute(() -> recipes.findById(new RecipeId(recipeId)))
                .filter(found -> found.isVisibleTo(viewerId))
                .orElseThrow(() -> new ResourceNotFoundException("Receita"));
        return new Viewed(view(recipeId), viewerId.map(recipe::isOwnedBy).orElse(false));
    }

    private Recipe owned(UUID recipeId, UUID ownerId) {
        return recipes.findById(new RecipeId(recipeId))
                .filter(recipe -> recipe.isOwnedBy(ownerId))
                .orElseThrow(() -> new ResourceNotFoundException("Receita"));
    }

    private RecipeView view(UUID recipeId) {
        return readModel.detail(recipeId).orElseThrow(() -> new ResourceNotFoundException("Receita"));
    }

    private RecipeDetails details(Command command) {
        List<IngredientInput> inputs = command.ingredients() == null ? List.of() : command.ingredients();
        List<UUID> productIds = inputs.stream().map(IngredientInput::productId).filter(id -> id != null).distinct().toList();
        Map<UUID, CatalogProduct> products = catalog.describe(productIds).stream()
                .collect(Collectors.toMap(CatalogProduct::id, Function.identity()));
        List<Ingredient> ingredients = inputs.stream()
                .map(input -> input.productId() == null
                        ? Ingredient.generic(input.name(), input.brand(), input.quantity(), input.unit())
                        : fromCatalog(products.get(input.productId()), input))
                .toList();
        return new RecipeDetails(command.name(), command.kind(), command.application(), command.stage(),
                command.batchLiters(), command.targetEc(), command.targetPh(), command.notes(), ingredients);
    }

    private static Ingredient fromCatalog(CatalogProduct product, IngredientInput input) {
        return new Ingredient(product.id(), product.name(), product.brand(), input.quantity(), input.unit());
    }

    public record Command(String name, RecipeKind kind, RecipeApplication application, RecipeStage stage,
                          BigDecimal batchLiters, BigDecimal targetEc, BigDecimal targetPh, String notes,
                          RecipeVisibility visibility, List<IngredientInput> ingredients) {

        public Command {
            visibility = visibility == null ? RecipeVisibility.PRIVATE : visibility;
        }
    }

    /** {@code productId} para produto de marca do catálogo; senão {@code name} (e {@code brand} opcional). */
    public record IngredientInput(UUID productId, String name, String brand, BigDecimal quantity, IngredientUnit unit) {
    }

    public record Viewed(RecipeView recipe, boolean ownedByViewer) {
    }
}
