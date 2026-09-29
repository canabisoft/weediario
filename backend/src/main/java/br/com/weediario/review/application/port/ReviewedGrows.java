package br.com.weediario.review.application.port;

import br.com.weediario.review.domain.model.ReviewedGrow;

import java.util.Optional;
import java.util.UUID;

/**
 * Consulta ao contexto de diários (anti-corruption layer).
 */
public interface ReviewedGrows {

    Optional<ReviewedGrow> find(UUID diaryId);
}
