package br.com.weediario.review.domain.model;

import java.util.UUID;

/**
 * O que o contexto de reviews precisa saber sobre o cultivo avaliado (traduzido do contexto de diários).
 */
public record ReviewedGrow(UUID diaryId, UUID ownerId, boolean completed, String strainName, String breeder) {
}
