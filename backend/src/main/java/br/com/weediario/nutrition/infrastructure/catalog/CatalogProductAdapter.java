package br.com.weediario.nutrition.infrastructure.catalog;

import br.com.weediario.catalog.application.usecase.ResolveCatalogUseCase;
import br.com.weediario.nutrition.application.port.ProductCatalog;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class CatalogProductAdapter implements ProductCatalog {

    private final ResolveCatalogUseCase catalog;

    public CatalogProductAdapter(ResolveCatalogUseCase catalog) {
        this.catalog = catalog;
    }

    @Override
    public List<CatalogProduct> describe(List<UUID> productIds) {
        return catalog.products(productIds).stream()
                .map(item -> new CatalogProduct(item.id(), item.name(), item.parentName()))
                .toList();
    }
}
