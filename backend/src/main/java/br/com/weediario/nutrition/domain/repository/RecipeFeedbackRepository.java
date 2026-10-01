package br.com.weediario.nutrition.domain.repository;

import br.com.weediario.nutrition.domain.model.CommentReaction;
import br.com.weediario.nutrition.domain.model.RecipeComment;
import br.com.weediario.nutrition.domain.model.RecipeId;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Avaliações e comentários das receitas. */
public interface RecipeFeedbackRepository {

    /** Cria ou troca a nota do grower para a receita. */
    void rate(RecipeId recipeId, UUID raterId, int stars, Instant ratedAt);

    void removeRating(RecipeId recipeId, UUID raterId);

    Optional<Integer> ratingOf(RecipeId recipeId, UUID raterId);

    RecipeComment save(RecipeComment comment);

    Optional<RecipeComment> findComment(UUID commentId);

    /** Se quem escreveu o comentário tem conta ativa (comentário de conta suspensa ou banida sai do ar). */
    boolean commentAuthorActive(UUID commentId);

    void deleteComment(UUID commentId);

    long countCommentsByAuthorSince(UUID authorId, Instant since);

    /**
     * Reação anônima num comentário: guarda só o pseudônimo de quem reagiu (diferente em cada comentário).
     * Idempotente.
     */
    void addCommentReaction(UUID commentId, String reactor, CommentReaction type);

    void removeCommentReaction(UUID commentId, String reactor, CommentReaction type);

    /** Contagem por tipo de cada comentário pedido (sem reação, fica de fora do mapa). */
    Map<UUID, Map<CommentReaction, Long>> commentReactionCounts(Collection<UUID> commentIds);

    /** Reações de quem está vendo, a partir do pseudônimo dele em cada comentário. */
    Map<UUID, Set<CommentReaction>> commentReactionsBy(Map<UUID, String> reactorByComment);
}
