package br.com.weediario.review.domain.model;

import java.util.UUID;

/**
 * O que o contexto de reviews precisa saber sobre o cultivo avaliado (traduzido do contexto de diários).
 *
 * @param strainId  strain do catálogo cultivada
 * @param breederId breeder/banco do catálogo; nulo quando o diário não informa
 */
public record ReviewedGrow(UUID diaryId, UUID ownerId, boolean completed, UUID strainId, UUID breederId) {
}
