package br.com.weediario.review.application.port;

import java.util.UUID;

/**
 * Avisa o catálogo que a strain ganhou uma avaliação pós-colheita: se for uma semente em teste, isso pode
 * completar o teste da comunidade e levá-la ao banco oficial.
 */
public interface StrainTesting {

    /** Chamado depois que a review já foi salva (fora da transação dela): uma falha aqui não desfaz a review. */
    void reviewed(UUID strainId);
}
