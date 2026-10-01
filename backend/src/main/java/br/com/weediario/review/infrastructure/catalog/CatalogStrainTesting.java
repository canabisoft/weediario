package br.com.weediario.review.infrastructure.catalog;

import br.com.weediario.catalog.application.usecase.PromoteTestingStrainsUseCase;
import br.com.weediario.review.application.port.StrainTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Liga a review publicada à promoção das sementes em teste do catálogo.
 */
@Component
public class CatalogStrainTesting implements StrainTesting {

    private static final Logger log = LoggerFactory.getLogger(CatalogStrainTesting.class);

    private final PromoteTestingStrainsUseCase promote;

    public CatalogStrainTesting(PromoteTestingStrainsUseCase promote) {
        this.promote = promote;
    }

    @Override
    public void reviewed(UUID strainId) {
        try {
            promote.promoteIfReady(strainId);
        } catch (RuntimeException e) {
            // A review já foi publicada; o job periódico do catálogo tenta de novo.
            log.warn("Não foi possível conferir o teste da strain {}: {}", strainId, e.getMessage());
        }
    }
}
