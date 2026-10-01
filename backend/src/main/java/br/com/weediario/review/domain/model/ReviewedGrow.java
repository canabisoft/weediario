package br.com.weediario.review.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * O que o contexto de reviews precisa saber sobre o cultivo avaliado (traduzido do contexto de diários).
 *
 * @param strainId   strain do catálogo cultivada
 * @param breederId  breeder/banco do catálogo; nulo quando o diário não informa
 * @param productIds produtos do catálogo usados no cultivo (os únicos que a review pode avaliar)
 */
public record ReviewedGrow(UUID diaryId, UUID ownerId, boolean completed, UUID strainId, UUID breederId,
                           List<UUID> productIds) {

    public ReviewedGrow {
        productIds = productIds == null ? List.of() : List.copyOf(productIds);
    }

    public ReviewedGrow(UUID diaryId, UUID ownerId, boolean completed, UUID strainId, UUID breederId) {
        this(diaryId, ownerId, completed, strainId, breederId, List.of());
    }
}
