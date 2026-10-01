package br.com.weediario.nutrition.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Aggregate root: receita de nutrição de um grower.
 * <p>
 * Pública, aparece no perfil do autor e pode ser replicada por outros growers. A réplica é uma receita nova,
 * do grower que replicou (ele pode ajustar), que guarda de onde veio para dar o crédito ao autor original.
 */
public final class Recipe {

    private final RecipeId id;
    private final UUID ownerId;
    private RecipeDetails details;
    private RecipeVisibility visibility;
    private final RecipeId replicatedFrom;
    private final Instant createdAt;
    private Instant updatedAt;

    private Recipe(RecipeId id, UUID ownerId, RecipeDetails details, RecipeVisibility visibility,
                   RecipeId replicatedFrom, Instant createdAt, Instant updatedAt) {
        this.id = Guard.notNull(id, "Id");
        this.ownerId = Guard.notNull(ownerId, "Autor");
        this.details = Guard.notNull(details, "Receita");
        this.visibility = Guard.notNull(visibility, "Visibilidade");
        this.replicatedFrom = replicatedFrom;
        this.createdAt = Guard.notNull(createdAt, "Data de criação");
        this.updatedAt = Guard.notNull(updatedAt, "Data de atualização");
    }

    public static Recipe create(UUID ownerId, RecipeDetails details, RecipeVisibility visibility, Clock clock) {
        Instant now = clock.instant();
        return new Recipe(RecipeId.newId(), ownerId, details, visibility, null, now, now);
    }

    public static Recipe restore(RecipeId id, UUID ownerId, RecipeDetails details, RecipeVisibility visibility,
                                 RecipeId replicatedFrom, Instant createdAt, Instant updatedAt) {
        return new Recipe(id, ownerId, details, visibility, replicatedFrom, createdAt, updatedAt);
    }

    /**
     * Cópia para o perfil de outro grower. Só receitas públicas, e não faz sentido replicar a própria.
     * A cópia nasce privada: quem replicou decide se quer exibi-la.
     */
    public Recipe replicateFor(UUID growerId, Clock clock) {
        if (visibility != RecipeVisibility.PUBLIC) {
            throw new DomainException("Só receitas públicas podem ser replicadas");
        }
        if (isOwnedBy(growerId)) {
            throw new DomainException("Esta receita já é sua");
        }
        Instant now = clock.instant();
        return new Recipe(RecipeId.newId(), growerId, details, RecipeVisibility.PRIVATE, id, now, now);
    }

    /** Estrelas são a recomendação da comunidade: só em receita pública, e o autor não vota em si mesmo. */
    public void checkCanBeRatedBy(UUID raterId) {
        if (visibility != RecipeVisibility.PUBLIC) {
            throw new DomainException("Só receitas públicas podem ser avaliadas");
        }
        if (isOwnedBy(raterId)) {
            throw new DomainException("Você não pode avaliar a própria receita");
        }
    }

    /** Comentários existem para a conversa pública; o autor também pode responder. */
    public void checkAcceptsComments() {
        if (visibility != RecipeVisibility.PUBLIC) {
            throw new DomainException("Só receitas públicas recebem comentários");
        }
    }

    public void revise(RecipeDetails newDetails, RecipeVisibility newVisibility, Clock clock) {
        this.details = Guard.notNull(newDetails, "Receita");
        this.visibility = Guard.notNull(newVisibility, "Visibilidade");
        this.updatedAt = clock.instant();
    }

    public boolean isOwnedBy(UUID growerId) {
        return ownerId.equals(growerId);
    }

    /** Privadas só existem para o autor. */
    public boolean isVisibleTo(Optional<UUID> viewerId) {
        return visibility == RecipeVisibility.PUBLIC || viewerId.map(this::isOwnedBy).orElse(false);
    }

    public RecipeId id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public RecipeDetails details() {
        return details;
    }

    public RecipeVisibility visibility() {
        return visibility;
    }

    public Optional<RecipeId> replicatedFrom() {
        return Optional.ofNullable(replicatedFrom);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
