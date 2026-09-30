package br.com.weediario.diary.application.usecase;

import br.com.weediario.diary.application.GrowSetupView;
import br.com.weediario.diary.application.port.CatalogLookup;
import br.com.weediario.diary.domain.model.GrowEnvironment;
import br.com.weediario.diary.domain.model.GrowSetup;
import br.com.weediario.diary.domain.model.GrowSetupId;
import br.com.weediario.diary.domain.model.GrowerId;
import br.com.weediario.diary.domain.model.SetupSpec;
import br.com.weediario.diary.domain.repository.GrowSetupRepository;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * CRUD dos setups do próprio grower. Setups de outra pessoa respondem como inexistentes.
 */
public final class ManageGrowSetupsUseCase {

    public static final int MAX_SETUPS_PER_GROWER = 10;

    private final GrowSetupRepository setups;
    private final CatalogLookup catalog;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ManageGrowSetupsUseCase(GrowSetupRepository setups, CatalogLookup catalog, UnitOfWork unitOfWork,
                                   Clock clock) {
        this.setups = setups;
        this.catalog = catalog;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public List<GrowSetupView> list(UUID ownerId) {
        return unitOfWork.execute(() -> setups.findByOwner(new GrowerId(ownerId)).stream()
                .map(this::view)
                .toList());
    }

    public GrowSetupView create(UUID ownerId, Details details) {
        GrowerId owner = new GrowerId(ownerId);
        return unitOfWork.execute(() -> {
            if (setups.countByOwner(owner) >= MAX_SETUPS_PER_GROWER) {
                throw new DomainException("Limite de " + MAX_SETUPS_PER_GROWER + " setups atingido");
            }
            // Descrever os produtos também valida que todos existem no catálogo.
            catalog.products(details.productIds());
            GrowSetup setup = GrowSetup.create(owner, details.name(), details.environment(), details.spec(),
                    details.productIds(), clock);
            return view(setups.save(setup));
        });
    }

    public GrowSetupView revise(UUID ownerId, UUID setupId, Details details) {
        return unitOfWork.execute(() -> {
            GrowSetup setup = owned(ownerId, setupId);
            catalog.products(details.productIds());
            setup.revise(details.name(), details.environment(), details.spec(), details.productIds(), clock);
            return view(setups.save(setup));
        });
    }

    /** Diários criados com o setup mantêm a cópia deles; só a predefinição some. */
    public void delete(UUID ownerId, UUID setupId) {
        unitOfWork.run(() -> setups.delete(owned(ownerId, setupId).id()));
    }

    private GrowSetupView view(GrowSetup setup) {
        return GrowSetupView.of(setup, catalog.products(setup.productIds()));
    }

    private GrowSetup owned(UUID ownerId, UUID setupId) {
        return setups.findById(new GrowSetupId(setupId))
                .filter(setup -> setup.isOwnedBy(new GrowerId(ownerId)))
                .orElseThrow(() -> new ResourceNotFoundException("Setup"));
    }

    public record Details(String name, GrowEnvironment environment, SetupSpec spec, List<UUID> productIds) {

        public Details {
            productIds = productIds == null ? List.of() : productIds;
        }
    }
}
