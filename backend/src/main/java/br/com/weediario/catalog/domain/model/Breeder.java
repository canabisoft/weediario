package br.com.weediario.catalog.domain.model;

import br.com.weediario.catalog.domain.model.CatalogEnums.Source;
import br.com.weediario.catalog.domain.model.CatalogEnums.Status;
import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Breeder ou banco de sementes. Também é quem recebe as notas de germinação/estabilidade/atendimento nas reviews.
 */
public record Breeder(UUID id, String name, String nameKey, Status status, Source source, UUID createdBy,
                      Instant createdAt) {

    public Breeder {
        Guard.notNull(id, "Id");
        name = CatalogStrain.validName(name);
        nameKey = CatalogKey.breeder(name);
        if (nameKey.isEmpty()) {
            throw new DomainException("Nome de breeder inválido");
        }
        Guard.notNull(status, "Status");
        Guard.notNull(source, "Origem");
        Guard.notNull(createdAt, "Data de criação");
    }

    public static Breeder register(String name, Source source, UUID createdBy, Clock clock) {
        return new Breeder(UUID.randomUUID(), name, null, Status.UNVERIFIED, source, createdBy, clock.instant());
    }

    public Breeder verified() {
        return new Breeder(id, name, nameKey, Status.VERIFIED, source, createdBy, createdAt);
    }
}
