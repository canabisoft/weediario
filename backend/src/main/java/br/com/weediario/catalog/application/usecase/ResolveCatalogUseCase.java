package br.com.weediario.catalog.application.usecase;

import br.com.weediario.shared.domain.CommerceFilter;
import br.com.weediario.catalog.application.CatalogItem;
import br.com.weediario.catalog.application.Resolution;
import br.com.weediario.catalog.domain.model.Brand;
import br.com.weediario.catalog.domain.model.Breeder;
import br.com.weediario.catalog.domain.model.CatalogEnums.ProductCategory;
import br.com.weediario.catalog.domain.model.CatalogEnums.Source;
import br.com.weediario.catalog.domain.model.CatalogKey;
import br.com.weediario.catalog.domain.model.CatalogStrain;
import br.com.weediario.catalog.domain.model.Product;
import br.com.weediario.catalog.domain.model.StrainTraits;
import br.com.weediario.catalog.domain.repository.CatalogRepository;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * "Achar ou cadastrar" itens do catálogo sem duplicar:
 * <ol>
 *   <li>mesma chave normalizada (ou apelido) → devolve o existente;</li>
 *   <li>nome parecido (erro de digitação) e sem confirmação → devolve candidatos ("você quis dizer?");</li>
 *   <li>senão → cadastra como item da comunidade, ainda não verificado.</li>
 * </ol>
 */
public final class ResolveCatalogUseCase {

    public static final double SIMILARITY = 0.45;
    public static final int MAX_CANDIDATES = 5;
    public static final int MAX_CREATIONS_PER_DAY = 30;

    private final CatalogRepository catalog;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ResolveCatalogUseCase(CatalogRepository catalog, UnitOfWork unitOfWork, Clock clock) {
        this.catalog = catalog;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public Resolution resolveBreeder(String name, boolean confirmNew, UUID userId) {
        CommerceFilter.requireAllowed(name);
        return unitOfWork.execute(() -> {
            String key = CatalogKey.breeder(name);
            var existing = catalog.findBreederByKey(key);
            if (existing.isPresent()) {
                return Resolution.existing(CatalogItem.of(existing.get()));
            }
            if (!confirmNew) {
                List<CatalogItem> similar = catalog.similarBreeders(key, SIMILARITY, MAX_CANDIDATES).stream()
                        .map(CatalogItem::of).toList();
                if (!similar.isEmpty()) {
                    return Resolution.similar(similar);
                }
            }
            ensureCanCreate(userId);
            return Resolution.created(CatalogItem.of(catalog.saveBreeder(
                    Breeder.register(name, Source.COMMUNITY, userId, clock))));
        });
    }

    public Resolution resolveStrain(String name, UUID breederId, boolean confirmNew, UUID userId) {
        CommerceFilter.requireAllowed(name);
        return unitOfWork.execute(() -> {
            Breeder breeder = breederId == null ? null
                    : catalog.findBreeder(breederId).orElseThrow(() -> new ResourceNotFoundException("Breeder"));
            String key = CatalogKey.of(name);
            String breederKey = breeder == null ? "" : breeder.nameKey();
            var existing = catalog.findStrainByKey(key, breederKey);
            if (existing.isPresent()) {
                return Resolution.existing(CatalogItem.of(existing.get()));
            }
            if (!confirmNew) {
                List<CatalogItem> similar = catalog.similarStrains(key, breederKey, SIMILARITY, MAX_CANDIDATES).stream()
                        .map(CatalogItem::of).toList();
                if (!similar.isEmpty()) {
                    return Resolution.similar(similar);
                }
            }
            ensureCanCreate(userId);
            return Resolution.created(CatalogItem.of(catalog.saveStrain(
                    CatalogStrain.register(name, breeder, StrainTraits.EMPTY, Source.COMMUNITY, userId, clock))));
        });
    }

    public Resolution resolveBrand(String name, boolean confirmNew, UUID userId) {
        CommerceFilter.requireAllowed(name);
        return unitOfWork.execute(() -> {
            String key = CatalogKey.of(name);
            var existing = catalog.findBrandByKey(key);
            if (existing.isPresent()) {
                return Resolution.existing(CatalogItem.of(existing.get()));
            }
            if (!confirmNew) {
                List<CatalogItem> similar = catalog.similarBrands(key, SIMILARITY, MAX_CANDIDATES).stream()
                        .map(CatalogItem::of).toList();
                if (!similar.isEmpty()) {
                    return Resolution.similar(similar);
                }
            }
            ensureCanCreate(userId);
            return Resolution.created(CatalogItem.of(catalog.saveBrand(Brand.register(name, Source.COMMUNITY, userId, clock))));
        });
    }

    public Resolution resolveProduct(UUID brandId, String name, ProductCategory category, boolean confirmNew,
                                     UUID userId) {
        CommerceFilter.requireAllowed(name);
        return unitOfWork.execute(() -> {
            Brand brand = catalog.findBrand(brandId).orElseThrow(() -> new ResourceNotFoundException("Marca"));
            String key = CatalogKey.of(name);
            var existing = catalog.findProductByKey(brand.id(), key);
            if (existing.isPresent()) {
                return Resolution.existing(CatalogItem.of(existing.get(), brand));
            }
            if (!confirmNew) {
                List<CatalogItem> similar = catalog.similarProducts(brand.id(), key, SIMILARITY, MAX_CANDIDATES).stream()
                        .map(product -> CatalogItem.of(product, brand)).toList();
                if (!similar.isEmpty()) {
                    return Resolution.similar(similar);
                }
            }
            if (category == null) {
                throw new DomainException("Informe a categoria do produto");
            }
            ensureCanCreate(userId);
            return Resolution.created(CatalogItem.of(catalog.saveProduct(
                    Product.register(brand.id(), name, category, Source.COMMUNITY, userId, clock)), brand));
        });
    }

    /**
     * Uso interno (diários criados pela API sem escolher item do catálogo): acha pelo nome ou cadastra direto,
     * sem etapa de confirmação.
     */
    public CatalogItem ensureStrain(String strainName, String breederName, UUID userId) {
        CommerceFilter.requireAllowed(strainName, breederName);
        UUID breederId = breederName == null || breederName.isBlank() || CatalogKey.breeder(breederName).isEmpty()
                ? null
                : resolveBreeder(breederName, true, userId).item().id();
        return resolveStrain(strainName, breederId, true, userId).item();
    }

    public CatalogItem strain(UUID strainId) {
        return unitOfWork.execute(() -> catalog.findStrain(strainId).map(CatalogItem::of)
                .orElseThrow(() -> new ResourceNotFoundException("Strain")));
    }

    /** Descreve produtos na ordem pedida; falha se algum não existir. */
    public List<CatalogItem> products(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return unitOfWork.execute(() -> {
            List<Product> found = catalog.findProducts(productIds);
            if (found.size() != productIds.stream().distinct().count()) {
                throw new ResourceNotFoundException("Produto");
            }
            return productIds.stream().distinct()
                    .map(id -> found.stream().filter(product -> product.id().equals(id)).findFirst().orElseThrow())
                    .map(product -> CatalogItem.of(product, catalog.findBrand(product.brandId()).orElseThrow()))
                    .toList();
        });
    }

    private void ensureCanCreate(UUID userId) {
        if (userId != null
                && catalog.countCreatedBy(userId, clock.instant().minus(Duration.ofDays(1))) >= MAX_CREATIONS_PER_DAY) {
            throw new DomainException("Limite diário de cadastros no catálogo atingido. Tente amanhã.");
        }
    }
}
