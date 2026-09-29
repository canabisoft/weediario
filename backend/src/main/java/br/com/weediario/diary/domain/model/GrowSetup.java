package br.com.weediario.diary.domain.model;

import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;

/**
 * Predefinição de cultivo do grower (tenda, luz, substrato, nutrientes...), reutilizada ao abrir novos diários.
 * É privada: só o dono vê a lista de setups; o público só vê a cópia congelada dentro de cada diário.
 */
public final class GrowSetup {

    public static final int NAME_MAX_LENGTH = 60;

    private final GrowSetupId id;
    private final GrowerId ownerId;
    private String name;
    private GrowEnvironment environment;
    private SetupSpec spec;
    private final Instant createdAt;
    private Instant updatedAt;

    private GrowSetup(GrowSetupId id, GrowerId ownerId, String name, GrowEnvironment environment, SetupSpec spec,
                      Instant createdAt, Instant updatedAt) {
        this.id = Guard.notNull(id, "Id");
        this.ownerId = Guard.notNull(ownerId, "Dono");
        this.name = validName(name);
        this.environment = Guard.notNull(environment, "Ambiente");
        this.spec = spec == null ? SetupSpec.EMPTY : spec;
        this.createdAt = Guard.notNull(createdAt, "Data de criação");
        this.updatedAt = Guard.notNull(updatedAt, "Data de atualização");
    }

    public static GrowSetup create(GrowerId ownerId, String name, GrowEnvironment environment, SetupSpec spec,
                                   Clock clock) {
        Instant now = clock.instant();
        return new GrowSetup(GrowSetupId.newId(), ownerId, name, environment, spec, now, now);
    }

    public static GrowSetup restore(GrowSetupId id, GrowerId ownerId, String name, GrowEnvironment environment,
                                    SetupSpec spec, Instant createdAt, Instant updatedAt) {
        return new GrowSetup(id, ownerId, name, environment, spec, createdAt, updatedAt);
    }

    public void revise(String newName, GrowEnvironment newEnvironment, SetupSpec newSpec, Clock clock) {
        this.name = validName(newName);
        this.environment = Guard.notNull(newEnvironment, "Ambiente");
        this.spec = newSpec == null ? SetupSpec.EMPTY : newSpec;
        this.updatedAt = clock.instant();
    }

    public SetupSnapshot snapshot() {
        return new SetupSnapshot(name, environment, spec);
    }

    public boolean isOwnedBy(GrowerId grower) {
        return ownerId.equals(grower);
    }

    private static String validName(String name) {
        return Guard.lengthBetween(Guard.notBlank(name, "Nome do setup"), 1, NAME_MAX_LENGTH, "Nome do setup");
    }

    public GrowSetupId id() {
        return id;
    }

    public GrowerId ownerId() {
        return ownerId;
    }

    public String name() {
        return name;
    }

    public GrowEnvironment environment() {
        return environment;
    }

    public SetupSpec spec() {
        return spec;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
