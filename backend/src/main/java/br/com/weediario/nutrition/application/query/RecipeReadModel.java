package br.com.weediario.nutrition.application.query;

import br.com.weediario.nutrition.domain.model.IngredientUnit;
import br.com.weediario.nutrition.domain.model.RecipeApplication;
import br.com.weediario.nutrition.domain.model.RecipeKind;
import br.com.weediario.nutrition.domain.model.RecipeStage;
import br.com.weediario.nutrition.domain.model.RecipeVisibility;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Consultas de receitas para listagens e detalhe. O autor aparece só pelo apelido público.
 * Contagens ({@code replications}, {@code diaries}) consideram apenas receitas/diários públicos de contas ativas.
 */
public interface RecipeReadModel {

    PageResult<RecipeSummary> publicRecipes(RecipeFilter filter, PageQuery page);

    PageResult<RecipeSummary> ofOwner(UUID ownerId, PageQuery page);

    PageResult<RecipeSummary> publicOfGrower(String username, PageQuery page);

    Optional<RecipeView> detail(UUID recipeId);

    /** Na ordem pedida, ignorando ids inexistentes. */
    List<RecipeSummary> summaries(Collection<UUID> recipeIds);

    /**
     * Comentários raiz em ordem cronológica (as respostas vêm por {@link #replyPreviews} e {@link #replies}).
     * Ocultados pela moderação aparecem para a moderação e para o próprio autor ("em análise").
     */
    PageResult<CommentRow> comments(UUID recipeId, boolean includeHidden, Optional<UUID> viewerId, PageQuery page);

    /**
     * As primeiras {@code perRoot} respostas de cada comentário raiz (mesmo filtro de visibilidade), em ordem
     * cronológica, e o total de cada um. Comentários raiz sem respostas ficam de fora do mapa.
     */
    Map<UUID, CommentReplies> replyPreviews(Collection<UUID> rootIds, boolean includeHidden, Optional<UUID> viewerId,
                                            int perRoot);

    /** Respostas de um comentário raiz ("ver mais"); vazio se o comentário raiz não é visível para quem vê. */
    PageResult<CommentRow> replies(UUID rootId, boolean includeHidden, Optional<UUID> viewerId, PageQuery page);

    Optional<CommentRow> comment(UUID commentId);

    /**
     * Receitas públicas sugeridas ao grower: do tipo pedido (se houver), que usem produtos que ele já usa nos
     * cultivos e setups, e bem avaliadas. Exclui as dele, as que ele já replicou e as já vinculadas ao cultivo.
     */
    List<Recommendation> recommendations(Optional<UUID> viewerId, RecipeKind kind, UUID excludeDiaryId, int limit);

    /** Nomes de ingredientes genéricos já usados em receitas públicas (para sugerir e evitar grafias diferentes). */
    List<String> suggestIngredientNames(String query, int limit);

    enum RecipeSort {
        RECENT,
        MOST_REPLICATED,
        MOST_USED,
        /** Média de estrelas ponderada pelo número de avaliações (poucas notas não dominam o topo). */
        TOP_RATED
    }

    record RecipeFilter(RecipeKind kind, String query, UUID productId, RecipeSort sort) {

        public RecipeFilter {
            query = query == null || query.isBlank() ? null : query.strip();
            sort = sort == null ? RecipeSort.RECENT : sort;
        }
    }

    record RecipeSummary(UUID id, String name, RecipeKind kind, RecipeApplication application, RecipeStage stage,
                         String author, RecipeVisibility visibility, BigDecimal batchLiters,
                         List<String> ingredientNames, long ingredientCount, long replications, long diaries,
                         Instant updatedAt, long ratingCount, BigDecimal rating) {
    }

    /** {@code authorId} é interno: a API expõe só o apelido. {@code parentId}: comentário raiz respondido. */
    record CommentRow(UUID id, UUID authorId, String author, String body, boolean hidden, Instant createdAt,
                      UUID parentId) {
    }

    record CommentReplies(List<CommentRow> items, long total) {
    }

    /** {@code sharedProducts}: quantos produtos de marca da receita o grower já usa. */
    record Recommendation(RecipeSummary recipe, long sharedProducts) {
    }

    record IngredientView(UUID productId, String name, String brand, BigDecimal quantity, IngredientUnit unit) {
    }

    /** Receita de onde esta foi replicada; só aparece enquanto a original for pública. */
    record RecipeOrigin(UUID id, String name, String author) {
    }

    record RecipeView(UUID id, String name, RecipeKind kind, RecipeApplication application, RecipeStage stage,
                      BigDecimal batchLiters, BigDecimal targetEc, BigDecimal targetPh, String notes,
                      RecipeVisibility visibility, String author, List<IngredientView> ingredients,
                      long replications, long diaries, RecipeOrigin replicatedFrom, Instant createdAt,
                      Instant updatedAt, long ratingCount, BigDecimal rating) {
    }
}
