package br.com.weediario.nutrition.application.usecase;

import br.com.weediario.nutrition.application.port.RecipeModeration;
import br.com.weediario.shared.domain.CommerceFilter;
import br.com.weediario.shared.domain.CommerceFilter.Verdict;
import br.com.weediario.nutrition.application.query.RecipeReadModel;
import br.com.weediario.nutrition.application.query.RecipeReadModel.CommentRow;
import br.com.weediario.nutrition.domain.model.Recipe;
import br.com.weediario.nutrition.domain.model.RecipeComment;
import br.com.weediario.nutrition.domain.model.RecipeId;
import br.com.weediario.nutrition.domain.model.RecipeVisibility;
import br.com.weediario.nutrition.domain.repository.RecipeFeedbackRepository;
import br.com.weediario.nutrition.domain.repository.RecipeRepository;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Estrelas (a recomendação da comunidade, que alimenta o ranking) e comentários das receitas públicas.
 */
public final class RecipeFeedbackUseCase {

    public static final int MAX_COMMENTS_PER_MINUTE = 5;
    public static final int MAX_COMMENTS_PER_DAY = 100;

    private final RecipeRepository recipes;
    private final RecipeFeedbackRepository feedback;
    private final RecipeReadModel readModel;
    private final RecipeModeration moderation;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public RecipeFeedbackUseCase(RecipeRepository recipes, RecipeFeedbackRepository feedback, RecipeReadModel readModel,
                                 RecipeModeration moderation, UnitOfWork unitOfWork, Clock clock) {
        this.moderation = moderation;
        this.recipes = recipes;
        this.feedback = feedback;
        this.readModel = readModel;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public Rating rate(UUID recipeId, UUID raterId, int stars) {
        if (stars < 1 || stars > 5) {
            throw new DomainException("A nota vai de 1 a 5 estrelas");
        }
        unitOfWork.run(() -> {
            Recipe recipe = visible(recipeId, Optional.of(raterId));
            recipe.checkCanBeRatedBy(raterId);
            feedback.rate(recipe.id(), raterId, stars, clock.instant());
        });
        return rating(recipeId, Optional.of(raterId));
    }

    public Rating removeRating(UUID recipeId, UUID raterId) {
        unitOfWork.run(() -> feedback.removeRating(visible(recipeId, Optional.of(raterId)).id(), raterId));
        return rating(recipeId, Optional.of(raterId));
    }

    /** Média, total e a nota de quem está vendo (se já avaliou). */
    public Rating rating(UUID recipeId, Optional<UUID> viewerId) {
        return unitOfWork.execute(() -> {
            Recipe recipe = visible(recipeId, viewerId);
            var view = readModel.detail(recipeId).orElseThrow(() -> new ResourceNotFoundException("Receita"));
            Integer mine = viewerId.flatMap(id -> feedback.ratingOf(recipe.id(), id)).orElse(null);
            boolean canRate = viewerId.map(id -> !recipe.isOwnedBy(id)).orElse(false)
                    && recipe.visibility() == RecipeVisibility.PUBLIC;
            return new Rating(view.rating(), view.ratingCount(), mine, canRate);
        });
    }

    public PageResult<CommentView> comments(UUID recipeId, Optional<UUID> viewerId, boolean moderator, PageQuery page) {
        return unitOfWork.execute(() -> {
            Recipe recipe = visible(recipeId, viewerId);
            return readModel.comments(recipeId, moderator, viewerId, page).map(row -> view(row, recipe, viewerId, moderator));
        });
    }

    /** Filtro anti-comércio: contato/PIX é bloqueado; cara de anúncio fica oculto e vai para a moderação. */
    public CommentView postComment(UUID recipeId, UUID authorId, String body) {
        Verdict verdict = CommerceFilter.requireAllowedOrHeld(body);
        return unitOfWork.execute(() -> {
            Recipe recipe = visible(recipeId, Optional.of(authorId));
            Instant now = clock.instant();
            if (feedback.countCommentsByAuthorSince(authorId, now.minus(Duration.ofMinutes(1))) >= MAX_COMMENTS_PER_MINUTE
                    || feedback.countCommentsByAuthorSince(authorId, now.minus(Duration.ofDays(1))) >= MAX_COMMENTS_PER_DAY) {
                throw new DomainException("Muitos comentários em pouco tempo. Tente novamente em instantes.");
            }
            RecipeComment posted = RecipeComment.post(recipe, authorId, body, clock);
            if (verdict.held()) {
                posted.holdForModeration();
            }
            RecipeComment comment = feedback.save(posted);
            if (verdict.held()) {
                moderation.commentHeld(comment.id(), verdict.reason());
            }
            return readModel.comment(comment.id())
                    .map(row -> view(row, recipe, Optional.of(authorId), false))
                    .orElseThrow();
        });
    }

    public void deleteComment(UUID recipeId, UUID commentId, UUID requesterId, boolean moderator) {
        unitOfWork.run(() -> {
            Recipe recipe = visible(recipeId, Optional.of(requesterId));
            RecipeComment comment = commentOf(recipe, commentId);
            if (!comment.canBeDeletedBy(requesterId, recipe, moderator)) {
                throw new ResourceNotFoundException("Comentário");
            }
            feedback.deleteComment(commentId);
        });
    }

    private Recipe visible(UUID recipeId, Optional<UUID> viewerId) {
        return recipes.findById(new RecipeId(recipeId))
                .filter(recipe -> recipe.isVisibleTo(viewerId))
                .orElseThrow(() -> new ResourceNotFoundException("Receita"));
    }

    private RecipeComment commentOf(Recipe recipe, UUID commentId) {
        return feedback.findComment(commentId)
                .filter(comment -> comment.recipeId().equals(recipe.id()))
                .orElseThrow(() -> new ResourceNotFoundException("Comentário"));
    }

    private static CommentView view(CommentRow row, Recipe recipe, Optional<UUID> viewerId, boolean moderator) {
        boolean mine = viewerId.map(row.authorId()::equals).orElse(false);
        boolean canDelete = moderator || mine || viewerId.map(recipe::isOwnedBy).orElse(false);
        boolean canReport = viewerId.isPresent() && !mine;
        return new CommentView(row.id(), row.author(), row.body(), row.createdAt(), row.hidden(), canDelete, canReport);
    }

    /** {@code mine}: nota de quem está vendo; {@code canRate}: logado, não é o autor e a receita é pública. */
    public record Rating(BigDecimal average, long count, Integer mine, boolean canRate) {
    }

    public record CommentView(UUID id, String author, String body, Instant createdAt, boolean hidden, boolean canDelete,
                              boolean canReport) {
    }
}
