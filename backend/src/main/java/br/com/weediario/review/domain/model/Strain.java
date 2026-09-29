package br.com.weediario.review.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;

/**
 * Genética no catálogo da comunidade. É criada automaticamente na primeira review, a partir da strain
 * informada no diário; o nome exibido é o da primeira pessoa que a cadastrou.
 */
public final class Strain {

    public static final int NAME_MAX_LENGTH = 80;

    private final StrainId id;
    private final String name;
    private final String breeder;
    private final StrainKey key;
    private final Instant createdAt;

    private Strain(StrainId id, String name, String breeder, StrainKey key, Instant createdAt) {
        this.id = Guard.notNull(id, "Id");
        this.name = Guard.lengthBetween(Guard.notBlank(name, "Strain"), 1, NAME_MAX_LENGTH, "Strain");
        this.breeder = breeder == null || breeder.isBlank() ? null : breeder.strip();
        this.key = Guard.notNull(key, "Chave");
        if (this.key.name().isEmpty()) {
            throw new DomainException("Nome de strain inválido");
        }
        this.createdAt = Guard.notNull(createdAt, "Data de criação");
    }

    public static Strain register(String name, String breeder, Clock clock) {
        return new Strain(StrainId.newId(), name, breeder, StrainKey.of(name, breeder), clock.instant());
    }

    public static Strain restore(StrainId id, String name, String breeder, StrainKey key, Instant createdAt) {
        return new Strain(id, name, breeder, key, createdAt);
    }

    public StrainId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String breeder() {
        return breeder;
    }

    public StrainKey key() {
        return key;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
