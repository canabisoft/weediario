package br.com.weediario.catalog.domain.model;

import br.com.weediario.shared.domain.DomainException;

/**
 * Andamento do teste de uma semente cadastrada pelo breeder. Ela entra no banco oficial quando
 * {@value #TESTERS_REQUIRED} cultivadores diferentes a cultivam e há pelo menos {@value #REVIEWS_REQUIRED}
 * avaliação pós-colheita. Representantes do próprio breeder não contam: o breeder não se auto-valida.
 *
 * @param testers growers distintos (contas ativas, fora do breeder) com diário da strain, em qualquer visibilidade
 * @param reviews avaliações pós-colheita publicadas (não ocultas) de contas ativas fora do breeder
 */
public record TestingProgress(long testers, long reviews) {

    public static final int TESTERS_REQUIRED = 3;
    public static final int REVIEWS_REQUIRED = 1;

    public TestingProgress {
        if (testers < 0 || reviews < 0) {
            throw new DomainException("Andamento do teste inválido");
        }
    }

    public boolean complete() {
        return testers >= TESTERS_REQUIRED && reviews >= REVIEWS_REQUIRED;
    }
}
