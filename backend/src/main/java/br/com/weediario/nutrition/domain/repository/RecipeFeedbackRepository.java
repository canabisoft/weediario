package br.com.weediario.nutrition.domain.repository;

import br.com.weediario.nutrition.domain.model.RecipeComment;
import br.com.weediario.nutrition.domain.model.RecipeId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Avaliações e comentários das receitas. */
public interface RecipeFeedbackRepository {

    /** Cria ou troca a nota do grower para a receita. */
    void rate(RecipeId recipeId, UUID raterId, int stars, Instant ratedAt);

    void removeRating(RecipeId recipeId, UUID raterId);

    Optional<Integer> ratingOf(RecipeId recipeId, UUID raterId);

    RecipeComment save(RecipeComment comment);

    Optional<RecipeComment> findComment(UUID commentId);

    void deleteComment(UUID commentId);

    long countCommentsByAuthorSince(UUID authorId, Instant since);

    /** Registra a denúncia (uma por grower) e devolve quantas denúncias distintas o comentário tem. */

}
