package br.com.weediario.catalog.application.usecase;

import br.com.weediario.catalog.application.CatalogItem;
import br.com.weediario.catalog.application.ModeratorOnlyException;
import br.com.weediario.catalog.domain.model.CatalogStrain;
import br.com.weediario.catalog.domain.model.StrainTraits;
import br.com.weediario.catalog.domain.repository.CatalogRepository;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.util.UUID;

/**
 * Curadoria do catálogo.
 * <ul>
 *   <li>Qualquer grower logado pode <strong>completar</strong> informações que ainda faltam (nunca sobrescreve).</li>
 *   <li>Moderadores podem corrigir qualquer campo, marcar como verificado e juntar duplicatas.</li>
 * </ul>
 */
public final class CurateCatalogUseCase {

    public enum Kind {
        STRAIN,
        BREEDER,
        BRAND,
        PRODUCT
    }

    private final CatalogRepository catalog;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public CurateCatalogUseCase(CatalogRepository catalog, UnitOfWork unitOfWork, Clock clock) {
        this.catalog = catalog;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    /**
     * @return {@code true} se algo foi acrescentado
     */
    public boolean contribute(UUID strainId, StrainTraits contribution, boolean moderator) {
        return unitOfWork.execute(() -> {
            CatalogStrain strain = strain(strainId);
            boolean changed;
            if (moderator) {
                strain.correct(contribution, clock);
                changed = true;
            } else {
                changed = strain.enrich(contribution, clock);
            }
            if (changed) {
                catalog.saveStrain(strain);
            }
            return changed;
        });
    }

    public void verify(Kind kind, UUID id, boolean moderator) {
        requireModerator(moderator);
        unitOfWork.run(() -> {
            switch (kind) {
                case STRAIN -> {
                    CatalogStrain strain = strain(id);
                    strain.verify(clock);
                    catalog.saveStrain(strain);
                }
                case BREEDER -> catalog.saveBreeder(catalog.findBreeder(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Breeder")).verified());
                case BRAND -> catalog.saveBrand(catalog.findBrand(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Marca")).verified());
                case PRODUCT -> catalog.saveProduct(catalog.findProduct(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Produto")).verified());
            }
        });
    }

    /**
     * Junta uma strain duplicada na principal. O nome da duplicada vira apelido, então quem digitar
     * aquele nome no futuro cai direto na principal.
     */
    public CatalogItem merge(UUID duplicateId, UUID targetId, boolean moderator) {
        requireModerator(moderator);
        if (duplicateId.equals(targetId)) {
            throw new DomainException("Escolha duas strains diferentes");
        }
        return unitOfWork.execute(() -> {
            CatalogStrain duplicate = strain(duplicateId);
            CatalogStrain target = strain(targetId);
            // Informações que só a duplicada tinha não se perdem.
            if (target.enrich(duplicate.traits(), clock)) {
                catalog.saveStrain(target);
            }
            catalog.mergeStrain(duplicate.id(), target.id());
            return CatalogItem.of(target);
        });
    }

    private CatalogStrain strain(UUID id) {
        return catalog.findStrain(id).orElseThrow(() -> new ResourceNotFoundException("Strain"));
    }

    private static void requireModerator(boolean moderator) {
        if (!moderator) {
            throw new ModeratorOnlyException();
        }
    }
}
