package br.com.weediario.nutrition.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Comentário numa receita pública (dúvidas, ajustes, resultados). O autor aparece só pelo apelido; nenhum dado de
 * origem é guardado. Denúncias e decisões ficam no contexto de moderação (ocultado depois de 3 denúncias).
 * <p>
 * Respostas têm um nível só: {@code parentId} aponta sempre para um comentário raiz da mesma receita. Responder uma
 * resposta anexa a nova ao mesmo comentário raiz. Apagar o comentário raiz apaga as respostas junto.
 */
public final class RecipeComment {

    public static final int BODY_MAX_LENGTH = 1000;

    private final UUID id;
    private final RecipeId recipeId;
    private final UUID parentId;
    private final UUID authorId;
    private final String body;
    private boolean hidden;
    private final Instant createdAt;

    private RecipeComment(UUID id, RecipeId recipeId, UUID parentId, UUID authorId, String body, boolean hidden,
                          Instant createdAt) {
        this.id = Guard.notNull(id, "Id");
        this.recipeId = Guard.notNull(recipeId, "Receita");
        if (id.equals(parentId)) {
            throw new DomainException("Um comentário não responde a si mesmo");
        }
        this.parentId = parentId;
        this.authorId = Guard.notNull(authorId, "Autor");
        this.body = validBody(body);
        this.hidden = hidden;
        this.createdAt = Guard.notNull(createdAt, "Data de criação");
    }

    public static RecipeComment post(Recipe recipe, UUID authorId, String body, Clock clock) {
        recipe.checkAcceptsComments();
        return new RecipeComment(UUID.randomUUID(), recipe.id(), null, authorId, body, false, clock.instant());
    }

    /** Resposta a {@code answered}; se ele já for uma resposta, a nova fica no mesmo comentário raiz. */
    public static RecipeComment reply(Recipe recipe, RecipeComment answered, UUID authorId, String body, Clock clock) {
        recipe.checkAcceptsComments();
        if (!answered.recipeId.equals(recipe.id())) {
            throw new DomainException("Comentário não encontrado");
        }
        return new RecipeComment(UUID.randomUUID(), recipe.id(), answered.rootId(), authorId, body, false,
                clock.instant());
    }

    public static RecipeComment restore(UUID id, RecipeId recipeId, UUID parentId, UUID authorId, String body,
                                        boolean hidden, Instant createdAt) {
        return new RecipeComment(id, recipeId, parentId, authorId, body, hidden, createdAt);
    }

    /** Comentário raiz da conversa: ele mesmo ou aquele a que responde. */
    public UUID rootId() {
        return parentId == null ? id : parentId;
    }

    /** Oculto (retido ou denunciado) só aparece para quem escreveu. */
    public boolean isVisibleTo(UUID viewerId) {
        return !hidden || authorId.equals(viewerId);
    }

    /** O autor apaga o próprio comentário; o dono da receita e moderadores apagam qualquer um nela. */
    public boolean canBeDeletedBy(UUID requesterId, Recipe recipe, boolean moderator) {
        return moderator || authorId.equals(requesterId) || recipe.isOwnedBy(requesterId);
    }

    /** Retido pelo filtro anti-comércio: salvo, mas fora do público até a moderação decidir. */
    public void holdForModeration() {
        hidden = true;
    }

    /** Texto guardado como veio (o Angular faz o escape); só removemos caracteres de controle. */
    private static String validBody(String body) {
        String cleaned = Guard.notBlank(body, "Comentário").replaceAll("[\\p{Cntrl}&&[^\n\t]]", "").strip();
        return Guard.lengthBetween(cleaned, 1, BODY_MAX_LENGTH, "Comentário");
    }

    public UUID id() {
        return id;
    }

    public RecipeId recipeId() {
        return recipeId;
    }

    /** Comentário raiz respondido; vazio no próprio comentário raiz. */
    public Optional<UUID> parentId() {
        return Optional.ofNullable(parentId);
    }

    public UUID authorId() {
        return authorId;
    }

    public String body() {
        return body;
    }

    public boolean hidden() {
        return hidden;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
