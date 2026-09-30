package br.com.weediario.catalog.domain.model;

import br.com.weediario.catalog.domain.model.CatalogEnums.ProductCategory;
import br.com.weediario.catalog.domain.model.CatalogEnums.Source;
import br.com.weediario.catalog.domain.model.CatalogEnums.Status;
import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Produto de uma marca (ex.: "Bio-Grow" da BioBizz, categoria nutriente). Identidade: marca + nome normalizado.
 */
public record Product(UUID id, UUID brandId, String name, String nameKey, ProductCategory category, Status status,
                      Source source, UUID createdBy, Instant createdAt) {

    public Product {
        Guard.notNull(id, "Id");
        Guard.notNull(brandId, "Marca");
        name = CatalogStrain.validName(name);
        nameKey = CatalogKey.of(name);
        if (nameKey.isEmpty()) {
            throw new DomainException("Nome de produto inválido");
        }
        Guard.notNull(category, "Categoria");
        Guard.notNull(status, "Status");
        Guard.notNull(source, "Origem");
        Guard.notNull(createdAt, "Data de criação");
    }

    public static Product register(UUID brandId, String name, ProductCategory category, Source source, UUID createdBy,
                                   Clock clock) {
        return new Product(UUID.randomUUID(), brandId, name, null, category, Status.UNVERIFIED, source, createdBy,
                clock.instant());
    }

    public Product verified() {
        return new Product(id, brandId, name, nameKey, category, Status.VERIFIED, source, createdBy, createdAt);
    }
}
