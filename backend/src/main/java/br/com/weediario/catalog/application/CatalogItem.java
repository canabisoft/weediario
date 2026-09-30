package br.com.weediario.catalog.application;

import br.com.weediario.catalog.domain.model.Brand;
import br.com.weediario.catalog.domain.model.Breeder;
import br.com.weediario.catalog.domain.model.CatalogStrain;
import br.com.weediario.catalog.domain.model.Product;

import java.util.UUID;

/**
 * Item do catálogo como os outros contextos e a API o enxergam.
 *
 * @param parentId   breeder da strain ou marca do produto
 * @param parentName nome do breeder/marca
 * @param category   categoria do produto (nulo para os demais tipos)
 */
public record CatalogItem(UUID id, String name, UUID parentId, String parentName, String category, String status,
                          String source) {

    public static CatalogItem of(CatalogStrain strain) {
        Breeder breeder = strain.breeder();
        return new CatalogItem(strain.id(), strain.name(), breeder == null ? null : breeder.id(),
                breeder == null ? null : breeder.name(), null, strain.status().name(), strain.source().name());
    }

    public static CatalogItem of(Breeder breeder) {
        return new CatalogItem(breeder.id(), breeder.name(), null, null, null, breeder.status().name(),
                breeder.source().name());
    }

    public static CatalogItem of(Brand brand) {
        return new CatalogItem(brand.id(), brand.name(), null, null, null, brand.status().name(), brand.source().name());
    }

    public static CatalogItem of(Product product, Brand brand) {
        return new CatalogItem(product.id(), product.name(), brand.id(), brand.name(), product.category().name(),
                product.status().name(), product.source().name());
    }
}
