package br.com.weediario.nutrition.application.usecase;

import br.com.weediario.nutrition.application.port.CommentReactorPseudonyms;
import br.com.weediario.nutrition.application.port.RecipeModeration;
import br.com.weediario.nutrition.application.port.RecipeReplyNotifier;
import br.com.weediario.shared.domain.CommerceFilter;
import br.com.weediario.shared.domain.CommerceFilter.Verdict;
import br.com.weediario.nutrition.application.query.RecipeReadModel;
import br.com.weediario.nutrition.application.query.RecipeReadModel.CommentReplies;
import br.com.weediario.nutrition.application.query.RecipeReadModel.CommentRow;
import br.com.weediario.nutrition.domain.model.CommentReaction;
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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Estrelas (a recomendação da comunidade, que alimenta o ranking) e comentários das receitas públicas, com respostas
 * em um nível (responder uma resposta anexa ao mesmo comentário raiz; apagar o raiz apaga as respostas) e reações
 * anônimas nos comentários.
 */
public final class RecipeFeedbackUseCase {

    public static final int MAX_COMMENTS_PER_MINUTE = 5;
    public static final int MAX_COMMENTS_PER_DAY = 100;
    /** Respostas que já vêm na listagem debaixo de cada comentário raiz; o resto vem pelo "ver mais". */
    public static final int REPLY_PREVIEW = 5;

    private final RecipeRepository recipes;
    private final RecipeFeedbackRepository feedback;
    private final RecipeReadModel readModel;
    private final RecipeModeration moderation;
    private final RecipeReplyNotifier replyNotifier;
    private final CommentReactorPseudonyms reactors;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public RecipeFeedbackUseCase(RecipeRepository recipes, RecipeFeedbackRepository feedback, RecipeReadModel readModel,
                                 RecipeModeration moderation, RecipeReplyNotifier replyNotifier,
                                 CommentReactorPseudonyms reactors, UnitOfWork unitOfWork, Clock clock) {
        this.moderation = moderation;
        this.recipes = recipes;
        this.feedback = feedback;
        this.readModel = readModel;
        this.replyNotifier = replyNotifier;
        this.reactors = reactors;
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

    /** Comentários raiz em ordem cronológica, cada um com as primeiras respostas e as reações. */
    public PageResult<CommentView> comments(UUID recipeId, Optional<UUID> viewerId, boolean moderator, PageQuery page) {
        return unitOfWork.execute(() -> {
            Recipe recipe = visible(recipeId, viewerId);
            PageResult<CommentRow> roots = readModel.comments(recipeId, moderator, viewerId, page);
            Map<UUID, CommentReplies> previews = readModel.replyPreviews(
                    roots.items().stream().map(CommentRow::id).toList(), moderator, viewerId, REPLY_PREVIEW);
            List<CommentRow> all = new ArrayList<>(roots.items());
            previews.values().forEach(replies -> all.addAll(replies.items()));
            Map<UUID, CommentReactions> reactions = reactionsOf(all, viewerId);
            return roots.map(root -> {
                CommentReplies replies = previews.getOrDefault(root.id(), new CommentReplies(List.of(), 0));
                List<CommentView> replyViews = replies.items().stream()
                        .map(reply -> view(reply, recipe, viewerId, moderator, reactions, List.of(), 0))
                        .toList();
                return view(root, recipe, viewerId, moderator, reactions, replyViews, replies.total());
            });
        });
    }

    /** Todas as respostas visíveis de um comentário raiz ("ver mais"), em ordem cronológica. */
    public PageResult<CommentView> replies(UUID recipeId, UUID commentId, Optional<UUID> viewerId, boolean moderator,
                                           PageQuery page) {
        return unitOfWork.execute(() -> {
            Recipe recipe = visible(recipeId, viewerId);
            RecipeComment comment = commentOf(recipe, commentId);
            PageResult<CommentRow> rows = readModel.replies(comment.rootId(), moderator, viewerId, page);
            Map<UUID, CommentReactions> reactions = reactionsOf(rows.items(), viewerId);
            return rows.map(row -> view(row, recipe, viewerId, moderator, reactions, List.of(), 0));
        });
    }

    public CommentView postComment(UUID recipeId, UUID authorId, String body) {
        return postComment(recipeId, authorId, body, Optional.empty());
    }

    /**
     * Filtro anti-comércio: contato/PIX é bloqueado; cara de anúncio fica oculto e vai para a moderação. Vale igual
     * para respostas; a resposta retida não avisa ninguém.
     *
     * @param answeredId comentário respondido (vazio para um comentário novo)
     */
    public CommentView postComment(UUID recipeId, UUID authorId, String body, Optional<UUID> answeredId) {
        Verdict verdict = CommerceFilter.requireAllowedOrHeld(body);
        return unitOfWork.execute(() -> {
            Recipe recipe = visible(recipeId, Optional.of(authorId));
            Instant now = clock.instant();
            if (feedback.countCommentsByAuthorSince(authorId, now.minus(Duration.ofMinutes(1))) >= MAX_COMMENTS_PER_MINUTE
                    || feedback.countCommentsByAuthorSince(authorId, now.minus(Duration.ofDays(1))) >= MAX_COMMENTS_PER_DAY) {
                throw new DomainException("Muitos comentários em pouco tempo. Tente novamente em instantes.");
            }
            Optional<RecipeComment> answered = answeredId.map(id -> visibleComment(recipe, id, authorId));
            RecipeComment posted = answered
                    .map(target -> RecipeComment.reply(recipe, target, authorId, body, clock))
                    .orElseGet(() -> RecipeComment.post(recipe, authorId, body, clock));
            if (verdict.held()) {
                posted.holdForModeration();
            }
            RecipeComment comment = feedback.save(posted);
            if (verdict.held()) {
                moderation.commentHeld(comment.id(), verdict.reason());
            } else {
                answered.ifPresent(target -> replyNotifier.replied(recipe.id().value(), target.authorId(), authorId));
            }
            return readModel.comment(comment.id())
                    .map(row -> view(row, recipe, Optional.of(authorId), false, Map.of(), List.of(), 0))
                    .orElseThrow();
        });
    }

    /** Apagar um comentário raiz apaga as respostas dele junto. */
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

    /** Reação anônima num comentário visível: só o pseudônimo (usuário, comentário) é guardado. */
    public CommentReactions react(UUID recipeId, UUID commentId, UUID userId, CommentReaction type) {
        return unitOfWork.execute(() -> {
            RecipeComment comment = visibleComment(visible(recipeId, Optional.of(userId)), commentId, userId);
            feedback.addCommentReaction(comment.id(), reactors.of(userId, comment.id()), type);
            return reactionsOf(comment.id(), userId);
        });
    }

    public CommentReactions unreact(UUID recipeId, UUID commentId, UUID userId, CommentReaction type) {
        return unitOfWork.execute(() -> {
            RecipeComment comment = visibleComment(visible(recipeId, Optional.of(userId)), commentId, userId);
            feedback.removeCommentReaction(comment.id(), reactors.of(userId, comment.id()), type);
            return reactionsOf(comment.id(), userId);
        });
    }

    private CommentReactions reactionsOf(UUID commentId, UUID userId) {
        Map<CommentReaction, Long> counts = feedback.commentReactionCounts(List.of(commentId))
                .getOrDefault(commentId, Map.of());
        Set<CommentReaction> mine = feedback.commentReactionsBy(Map.of(commentId, reactors.of(userId, commentId)))
                .getOrDefault(commentId, Set.of());
        return CommentReactions.of(counts, mine);
    }

    private Map<UUID, CommentReactions> reactionsOf(List<CommentRow> rows, Optional<UUID> viewerId) {
        List<UUID> ids = rows.stream().map(CommentRow::id).toList();
        Map<UUID, Map<CommentReaction, Long>> counts = feedback.commentReactionCounts(ids);
        Map<UUID, Set<CommentReaction>> mine = viewerId.map(viewer -> {
            Map<UUID, String> pseudonyms = new HashMap<>();
            ids.forEach(id -> pseudonyms.put(id, reactors.of(viewer, id)));
            return feedback.commentReactionsBy(pseudonyms);
        }).orElse(Map.of());
        Map<UUID, CommentReactions> result = new HashMap<>();
        ids.forEach(id -> result.put(id, CommentReactions.of(counts.getOrDefault(id, Map.of()),
                mine.getOrDefault(id, Set.of()))));
        return result;
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

    /** O comentário e, se for resposta, o comentário raiz dele precisam estar visíveis para quem interage. */
    private RecipeComment visibleComment(Recipe recipe, UUID commentId, UUID viewerId) {
        RecipeComment comment = commentOf(recipe, commentId);
        // Mesmo critério da listagem: oculto só para o autor, e nada de conta suspensa ou banida (nem no raiz).
        boolean visible = comment.isVisibleTo(viewerId) && feedback.commentAuthorActive(comment.id())
                && comment.parentId()
                .map(rootId -> feedback.findComment(rootId)
                        .map(root -> root.isVisibleTo(viewerId) && feedback.commentAuthorActive(rootId)).orElse(false))
                .orElse(true);
        if (!visible) {
            throw new ResourceNotFoundException("Comentário");
        }
        return comment;
    }

    private static CommentView view(CommentRow row, Recipe recipe, Optional<UUID> viewerId, boolean moderator,
                                    Map<UUID, CommentReactions> reactions, List<CommentView> replies, long replyCount) {
        boolean mine = viewerId.map(row.authorId()::equals).orElse(false);
        boolean canDelete = moderator || mine || viewerId.map(recipe::isOwnedBy).orElse(false);
        boolean canReport = viewerId.isPresent() && !mine;
        return new CommentView(row.id(), row.author(), row.body(), row.createdAt(), row.hidden(), canDelete, canReport,
                row.parentId(), reactions.getOrDefault(row.id(), CommentReactions.of(Map.of(), Set.of())),
                replyCount, replies);
    }

    /** {@code mine}: nota de quem está vendo; {@code canRate}: logado, não é o autor e a receita é pública. */
    public record Rating(BigDecimal average, long count, Integer mine, boolean canRate) {
    }

    /**
     * @param parentId   comentário raiz respondido (nulo nos comentários raiz)
     * @param reactions  contagens por tipo e as reações de quem está vendo (nunca quem reagiu)
     * @param replyCount total de respostas visíveis (só nos comentários raiz)
     * @param replies    as primeiras respostas, em ordem cronológica (o resto vem pelo "ver mais")
     */
    public record CommentView(UUID id, String author, String body, Instant createdAt, boolean hidden, boolean canDelete,
                              boolean canReport, UUID parentId, CommentReactions reactions, long replyCount,
                              List<CommentView> replies) {
    }

    public record CommentReactions(Map<CommentReaction, Long> counts, Set<CommentReaction> mine) {

        public static CommentReactions of(Map<CommentReaction, Long> counts, Set<CommentReaction> mine) {
            Map<CommentReaction, Long> all = new EnumMap<>(CommentReaction.class);
            for (CommentReaction type : CommentReaction.values()) {
                all.put(type, counts.getOrDefault(type, 0L));
            }
            return new CommentReactions(all, Set.copyOf(mine));
        }
    }
}
