package br.com.weediario.diary.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Predefinição de cultivo do grower (tenda, luz, substrato, nutrientes...), reutilizada ao abrir novos diários.
 * É privada: só o dono vê a lista de setups; o público só vê a cópia congelada dentro de cada diário.
 */
public final class GrowSetup {

    public static final int NAME_MAX_LENGTH = 60;
    public static final int MAX_PRODUCTS = 20;

    private final GrowSetupId id;
    private final GrowerId ownerId;
    private String name;
    private GrowEnvironment environment;
    private SetupSpec spec;
    private List<UUID> productIds;
    private final Instant createdAt;
    private Instant updatedAt;

    private GrowSetup(GrowSetupId id, GrowerId ownerId, String name, GrowEnvironment environment, SetupSpec spec,
                      List<UUID> productIds, Instant createdAt, Instant updatedAt) {
        this.id = Guard.notNull(id, "Id");
        this.ownerId = Guard.notNull(ownerId, "Dono");
        this.name = validName(name);
        this.environment = Guard.notNull(environment, "Ambiente");
        this.spec = spec == null ? SetupSpec.EMPTY : spec;
        this.productIds = validProducts(productIds);
        this.createdAt = Guard.notNull(createdAt, "Data de criação");
        this.updatedAt = Guard.notNull(updatedAt, "Data de atualização");
    }

    /**
     * @param productIds produtos do catálogo usados no setup (a existência é validada pelo caso de uso)
     */
    public static GrowSetup create(GrowerId ownerId, String name, GrowEnvironment environment, SetupSpec spec,
                                   List<UUID> productIds, Clock clock) {
        Instant now = clock.instant();
        return new GrowSetup(GrowSetupId.newId(), ownerId, name, environment, spec, productIds, now, now);
    }

    public static GrowSetup restore(GrowSetupId id, GrowerId ownerId, String name, GrowEnvironment environment,
                                    SetupSpec spec, List<UUID> productIds, Instant createdAt, Instant updatedAt) {
        return new GrowSetup(id, ownerId, name, environment, spec, productIds, createdAt, updatedAt);
    }

    public void revise(String newName, GrowEnvironment newEnvironment, SetupSpec newSpec, List<UUID> newProductIds,
                       Clock clock) {
        this.name = validName(newName);
        this.environment = Guard.notNull(newEnvironment, "Ambiente");
        this.spec = newSpec == null ? SetupSpec.EMPTY : newSpec;
        this.productIds = validProducts(newProductIds);
        this.updatedAt = clock.instant();
    }

    /**
     * @param products os produtos deste setup já descritos pelo catálogo (nome e marca)
     */
    public SetupSnapshot snapshot(List<ProductSnapshot> products) {
        return new SetupSnapshot(name, environment, spec, products);
    }

    public boolean isOwnedBy(GrowerId grower) {
        return ownerId.equals(grower);
    }

    private static List<UUID> validProducts(List<UUID> productIds) {
        List<UUID> distinct = productIds == null ? List.of() : List.copyOf(new LinkedHashSet<>(productIds));
        if (distinct.size() > MAX_PRODUCTS) {
            throw new DomainException("Máximo de " + MAX_PRODUCTS + " produtos por setup");
        }
        return distinct;
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

    public List<UUID> productIds() {
        return productIds;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
