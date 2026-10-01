package br.com.weediario.nutrition.domain.model;

import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Comentário numa receita pública (dúvidas, ajustes, resultados). O autor aparece só pelo apelido; nenhum dado de
 * origem é guardado. Como nos diários, é ocultado depois de {@link #HIDE_AFTER_REPORTS} denúncias distintas.
 */
public final class RecipeComment {

    public static final int BODY_MAX_LENGTH = 1000;
    public static final int HIDE_AFTER_REPORTS = 3;

    private final UUID id;
    private final RecipeId recipeId;
    private final UUID authorId;
    private final String body;
    private boolean hidden;
    private final Instant createdAt;

    private RecipeComment(UUID id, RecipeId recipeId, UUID authorId, String body, boolean hidden, Instant createdAt) {
        this.id = Guard.notNull(id, "Id");
        this.recipeId = Guard.notNull(recipeId, "Receita");
        this.authorId = Guard.notNull(authorId, "Autor");
        this.body = validBody(body);
        this.hidden = hidden;
        this.createdAt = Guard.notNull(createdAt, "Data de criação");
    }

    public static RecipeComment post(Recipe recipe, UUID authorId, String body, Clock clock) {
        recipe.checkAcceptsComments();
        return new RecipeComment(UUID.randomUUID(), recipe.id(), authorId, body, false, clock.instant());
    }

    public static RecipeComment restore(UUID id, RecipeId recipeId, UUID authorId, String body, boolean hidden,
                                        Instant createdAt) {
        return new RecipeComment(id, recipeId, authorId, body, hidden, createdAt);
    }

    /** O autor apaga o próprio comentário; o dono da receita e moderadores apagam qualquer um nela. */
    public boolean canBeDeletedBy(UUID requesterId, Recipe recipe, boolean moderator) {
        return moderator || authorId.equals(requesterId) || recipe.isOwnedBy(requesterId);
    }

    public boolean canBeReportedBy(UUID reporterId) {
        return !authorId.equals(reporterId);
    }

    public void registerReports(long distinctReports) {
        if (distinctReports >= HIDE_AFTER_REPORTS) {
            hidden = true;
        }
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
