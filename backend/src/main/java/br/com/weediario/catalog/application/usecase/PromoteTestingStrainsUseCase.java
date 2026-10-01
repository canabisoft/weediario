package br.com.weediario.catalog.application.usecase;

import br.com.weediario.catalog.domain.model.CatalogStrain;
import br.com.weediario.catalog.domain.repository.CatalogRepository;
import br.com.weediario.shared.application.UnitOfWork;

import java.time.Clock;
import java.util.UUID;

/**
 * Leva ao banco oficial as sementes em teste que completaram o teste da comunidade
 * (ver {@link br.com.weediario.catalog.domain.model.TestingProgress}). Roda logo depois de cada review publicada
 * e, por garantia, periodicamente para todas (um diário novo também pode completar o teste).
 */
public final class PromoteTestingStrainsUseCase {

    private final CatalogRepository catalog;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public PromoteTestingStrainsUseCase(CatalogRepository catalog, UnitOfWork unitOfWork, Clock clock) {
        this.catalog = catalog;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    /**
     * @return {@code true} se a strain entrou no banco oficial agora
     */
    public boolean promoteIfReady(UUID strainId) {
        return unitOfWork.execute(() -> {
            CatalogStrain strain = catalog.findStrain(strainId).orElse(null);
            if (strain == null || !strain.testing()) {
                return false;
            }
            boolean promoted = strain.promote(catalog.testingProgress(strainId), clock);
            if (promoted) {
                catalog.saveStrain(strain);
            }
            return promoted;
        });
    }

    /**
     * Cada strain em sua própria transação (transações curtas, sem travar o catálogo inteiro).
     *
     * @return quantas foram promovidas
     */
    public int promoteAllReady() {
        int promoted = 0;
        for (UUID id : unitOfWork.execute(catalog::testingStrainIds)) {
            if (promoteIfReady(id)) {
                promoted++;
            }
        }
        return promoted;
    }
}
