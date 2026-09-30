package br.com.weediario.catalog.domain.model;

import br.com.weediario.catalog.domain.model.CatalogEnums.Source;
import br.com.weediario.catalog.domain.model.CatalogEnums.Status;
import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Genética no catálogo. A identidade é nome + breeder normalizados: a mesma strain de breeders
 * diferentes são genéticas diferentes.
 */
public final class CatalogStrain {

    public static final int NAME_MAX_LENGTH = 80;

    private final UUID id;
    private final String name;
    private final String nameKey;
    private final Breeder breeder;
    private StrainTraits traits;
    private Status status;
    private final Source source;
    private final UUID createdBy;
    private final Instant createdAt;
    private Instant updatedAt;

    private CatalogStrain(UUID id, String name, Breeder breeder, StrainTraits traits, Status status, Source source,
                          UUID createdBy, Instant createdAt, Instant updatedAt) {
        this.id = Guard.notNull(id, "Id");
        this.name = validName(name);
        this.nameKey = CatalogKey.of(this.name);
        if (nameKey.isEmpty()) {
            throw new DomainException("Nome de strain inválido");
        }
        this.breeder = breeder;
        this.traits = traits == null ? StrainTraits.EMPTY : traits;
        this.status = Guard.notNull(status, "Status");
        this.source = Guard.notNull(source, "Origem");
        this.createdBy = createdBy;
        this.createdAt = Guard.notNull(createdAt, "Data de criação");
        this.updatedAt = Guard.notNull(updatedAt, "Data de atualização");
    }

    public static CatalogStrain register(String name, Breeder breeder, StrainTraits traits, Source source,
                                         UUID createdBy, Clock clock) {
        Instant now = clock.instant();
        return new CatalogStrain(UUID.randomUUID(), name, breeder, traits, Status.UNVERIFIED, source, createdBy, now, now);
    }

    public static CatalogStrain restore(UUID id, String name, Breeder breeder, StrainTraits traits, Status status,
                                        Source source, UUID createdBy, Instant createdAt, Instant updatedAt) {
        return new CatalogStrain(id, name, breeder, traits, status, source, createdBy, createdAt, updatedAt);
    }

    /** Enriquecimento pela comunidade ou pelo dataset: só completa campos vazios. */
    public boolean enrich(StrainTraits contribution, Clock clock) {
        StrainTraits merged = traits.fillMissingFrom(contribution);
        if (merged.equals(traits)) {
            return false;
        }
        traits = merged;
        updatedAt = clock.instant();
        return true;
    }

    /** Moderadores podem corrigir qualquer campo. */
    public void correct(StrainTraits corrected, Clock clock) {
        traits = Guard.notNull(corrected, "Características");
        updatedAt = clock.instant();
    }

    public void verify(Clock clock) {
        status = Status.VERIFIED;
        updatedAt = clock.instant();
    }

    public String breederKey() {
        return breeder == null ? "" : breeder.nameKey();
    }

    static String validName(String name) {
        String cleaned = Guard.notBlank(name, "Nome").replaceAll("\\p{Cntrl}", "").replaceAll("\\s+", " ");
        return Guard.lengthBetween(cleaned, 1, NAME_MAX_LENGTH, "Nome");
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String nameKey() {
        return nameKey;
    }

    public Breeder breeder() {
        return breeder;
    }

    public StrainTraits traits() {
        return traits;
    }

    public Status status() {
        return status;
    }

    public Source source() {
        return source;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
