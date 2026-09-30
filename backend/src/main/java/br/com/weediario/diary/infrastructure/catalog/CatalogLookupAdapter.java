package br.com.weediario.diary.infrastructure.catalog;

import br.com.weediario.catalog.application.CatalogItem;
import br.com.weediario.catalog.application.usecase.ResolveCatalogUseCase;
import br.com.weediario.diary.application.port.CatalogLookup;
import br.com.weediario.diary.domain.model.ProductSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class CatalogLookupAdapter implements CatalogLookup {

    private final ResolveCatalogUseCase catalog;

    public CatalogLookupAdapter(ResolveCatalogUseCase catalog) {
        this.catalog = catalog;
    }

    @Override
    public CatalogStrainRef strain(UUID strainId) {
        return ref(catalog.strain(strainId));
    }

    @Override
    public CatalogStrainRef ensureStrain(String name, String breeder, UUID userId) {
        return ref(catalog.ensureStrain(name, breeder, userId));
    }

    @Override
    public List<ProductSnapshot> products(List<UUID> productIds) {
        return catalog.products(productIds).stream()
                .map(item -> new ProductSnapshot(item.id(), item.name(), item.parentName(), item.category()))
                .toList();
    }

    private static CatalogStrainRef ref(CatalogItem item) {
        return new CatalogStrainRef(item.id(), item.name(), item.parentId(), item.parentName());
    }
}
