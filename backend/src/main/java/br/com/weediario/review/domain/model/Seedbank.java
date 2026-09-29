package br.com.weediario.review.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Banco de sementes no catálogo. Criado na primeira avaliação, a partir do breeder informado no diário;
 * o nome é normalizado igual ao das strains para que variações de digitação caiam no mesmo banco.
 */
public final class Seedbank {

    public static final int NAME_MAX_LENGTH = 80;

    private final UUID id;
    private final String name;
    private final String key;
    private final Instant createdAt;

    private Seedbank(UUID id, String name, String key, Instant createdAt) {
        this.id = Guard.notNull(id, "Id");
        this.name = Guard.lengthBetween(Guard.notBlank(name, "Banco de sementes"), 1, NAME_MAX_LENGTH, "Banco de sementes");
        this.key = Guard.notNull(key, "Chave");
        if (key.isEmpty()) {
            throw new DomainException("Nome de banco de sementes inválido");
        }
        this.createdAt = Guard.notNull(createdAt, "Data de criação");
    }

    public static Seedbank register(String name, Clock clock) {
        return new Seedbank(UUID.randomUUID(), name.strip(), keyOf(name), clock.instant());
    }

    public static Seedbank restore(UUID id, String name, String key, Instant createdAt) {
        return new Seedbank(id, name, key, createdAt);
    }

    public static String keyOf(String name) {
        return StrainKey.normalize(name);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String key() {
        return key;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
