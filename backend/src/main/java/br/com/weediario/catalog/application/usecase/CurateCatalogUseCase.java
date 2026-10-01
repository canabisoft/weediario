package br.com.weediario.catalog.application.usecase;

import br.com.weediario.shared.domain.CommerceFilter;
import br.com.weediario.catalog.application.CatalogItem;
import br.com.weediario.catalog.domain.model.Brand;
import br.com.weediario.catalog.domain.model.Breeder;
import br.com.weediario.catalog.domain.model.CatalogStrain;
import br.com.weediario.catalog.domain.model.Product;
import br.com.weediario.catalog.domain.model.StrainTraits;
import br.com.weediario.catalog.domain.repository.CatalogRepository;
import br.com.weediario.catalog.domain.repository.CatalogRepository.ItemKind;
import br.com.weediario.shared.application.ModeratorOnlyException;
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
        // Texto livre que vai a público: sem contato, PIX ou anúncio (aqui não há fila, então retenção também bloqueia).
        CommerceFilter.requireAllowed(contribution.genetics(), String.join(" ", contribution.flavors()),
                String.join(" ", contribution.effects()));
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
     * Junta um item duplicado no principal. O nome do duplicado vira apelido, então quem digitar
     * aquele nome no futuro cai direto no principal. Ao juntar breeders (ou marcas), strains (ou produtos)
     * com o mesmo nome nos dois também são juntadas.
     */
    public CatalogItem merge(Kind kind, UUID duplicateId, UUID targetId, boolean moderator) {
        requireModerator(moderator);
        if (duplicateId.equals(targetId)) {
            throw new DomainException("Escolha dois itens diferentes");
        }
        return unitOfWork.execute(() -> switch (kind) {
            case STRAIN -> CatalogItem.of(mergeStrains(duplicateId, targetId));
            case BREEDER -> {
                breeder(duplicateId);
                Breeder target = breeder(targetId);
                catalog.strainCollisions(duplicateId, targetId)
                        .forEach(collision -> mergeStrains(collision.duplicateId(), collision.targetId()));
                catalog.mergeBreeder(duplicateId, targetId);
                yield CatalogItem.of(target);
            }
            case BRAND -> {
                brand(duplicateId);
                Brand target = brand(targetId);
                catalog.productCollisions(duplicateId, targetId)
                        .forEach(collision -> catalog.mergeProduct(collision.duplicateId(), collision.targetId()));
                catalog.mergeBrand(duplicateId, targetId);
                yield CatalogItem.of(target);
            }
            case PRODUCT -> {
                product(duplicateId);
                Product target = product(targetId);
                catalog.mergeProduct(duplicateId, targetId);
                yield CatalogItem.of(target, brand(target.brandId()));
            }
        });
    }

    /** Recusa um cadastro errado ou spam. Só dá para apagar o que ninguém usa; o resto precisa ser juntado. */
    public void reject(Kind kind, UUID id, boolean moderator) {
        requireModerator(moderator);
        unitOfWork.run(() -> {
            switch (kind) {
                case STRAIN -> strain(id);
                case BREEDER -> breeder(id);
                case BRAND -> brand(id);
                case PRODUCT -> product(id);
            }
            if (!catalog.deleteIfUnused(ItemKind.valueOf(kind.name()), id)) {
                throw new DomainException("Este item já está em uso. Junte-o com o item certo em vez de apagar.");
            }
        });
    }

    private CatalogStrain mergeStrains(UUID duplicateId, UUID targetId) {
        CatalogStrain duplicate = strain(duplicateId);
        CatalogStrain target = strain(targetId);
        // Informações que só a duplicada tinha não se perdem.
        if (target.enrich(duplicate.traits(), clock)) {
            catalog.saveStrain(target);
        }
        catalog.mergeStrain(duplicate.id(), target.id());
        return target;
    }

    private Breeder breeder(UUID id) {
        return catalog.findBreeder(id).orElseThrow(() -> new ResourceNotFoundException("Breeder"));
    }

    private Brand brand(UUID id) {
        return catalog.findBrand(id).orElseThrow(() -> new ResourceNotFoundException("Marca"));
    }

    private Product product(UUID id) {
        return catalog.findProduct(id).orElseThrow(() -> new ResourceNotFoundException("Produto"));
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
