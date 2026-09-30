package br.com.weediario.catalog.domain.model;

import br.com.weediario.catalog.domain.model.CatalogEnums.Source;
import br.com.weediario.catalog.domain.model.CatalogEnums.Status;
import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Marca de insumos (fertilizantes, substratos, aditivos...).
 */
public record Brand(UUID id, String name, String nameKey, Status status, Source source, UUID createdBy,
                    Instant createdAt) {

    public Brand {
        Guard.notNull(id, "Id");
        name = CatalogStrain.validName(name);
        nameKey = CatalogKey.of(name);
        if (nameKey.isEmpty()) {
            throw new DomainException("Nome de marca inválido");
        }
        Guard.notNull(status, "Status");
        Guard.notNull(source, "Origem");
        Guard.notNull(createdAt, "Data de criação");
    }

    public static Brand register(String name, Source source, UUID createdBy, Clock clock) {
        return new Brand(UUID.randomUUID(), name, null, Status.UNVERIFIED, source, createdBy, clock.instant());
    }

    public Brand verified() {
        return new Brand(id, name, nameKey, Status.VERIFIED, source, createdBy, createdAt);
    }
}
