package br.com.weediario.catalog.domain.model;

import br.com.weediario.shared.domain.DomainException;

/**
 * Andamento do teste de uma semente cadastrada pelo breeder. Ela entra no banco oficial quando, além da avaliação do
 * próprio breeder, {@value #TESTERS_REQUIRED} cultivadores diferentes a cultivam e há pelo menos
 * {@value #REVIEWS_REQUIRED} avaliação pós-colheita. Representantes do próprio breeder não contam como testadores: o
 * breeder não se auto-valida — mas precisa ter cultivado e colhido a genética para registrar a primeira avaliação do
 * que esperar dela.
 *
 * @param testers         growers distintos (contas ativas, fora do breeder) com diário da strain, em qualquer visibilidade
 * @param reviews         avaliações pós-colheita publicadas (não ocultas) de contas ativas fora do breeder
 * @param breederReviewed se um representante do breeder já publicou a avaliação pós-colheita da genética
 */
public record TestingProgress(long testers, long reviews, boolean breederReviewed) {

    public static final int TESTERS_REQUIRED = 3;
    public static final int REVIEWS_REQUIRED = 1;

    public TestingProgress {
        if (testers < 0 || reviews < 0) {
            throw new DomainException("Andamento do teste inválido");
        }
    }

    public boolean complete() {
        return breederReviewed && testers >= TESTERS_REQUIRED && reviews >= REVIEWS_REQUIRED;
    }
}
