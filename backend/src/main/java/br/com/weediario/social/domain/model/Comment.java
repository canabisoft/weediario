package br.com.weediario.social.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Comentário em um diário. O autor aparece só pelo apelido; nenhum dado de origem (IP, aparelho) é guardado.
 * <p>
 * Moderação comunitária: depois de {@link #HIDE_AFTER_REPORTS} denúncias distintas o comentário é ocultado
 * automaticamente até um moderador decidir.
 */
public final class Comment {

    public static final int BODY_MAX_LENGTH = 2000;
    public static final int HIDE_AFTER_REPORTS = 3;

    private final UUID id;
    private final UUID diaryId;
    private final UUID authorId;
    private final String body;
    private boolean hidden;
    private final Instant createdAt;

    private Comment(UUID id, UUID diaryId, UUID authorId, String body, boolean hidden, Instant createdAt) {
        this.id = Guard.notNull(id, "Id");
        this.diaryId = Guard.notNull(diaryId, "Diário");
        this.authorId = Guard.notNull(authorId, "Autor");
        this.body = validBody(body);
        this.hidden = hidden;
        this.createdAt = Guard.notNull(createdAt, "Data de criação");
    }

    public static Comment post(CommentableDiary diary, UUID authorId, String body, Clock clock) {
        if (!diary.isVisibleTo(Optional.of(authorId))) {
            throw new DomainException("Diário não encontrado");
        }
        return new Comment(UUID.randomUUID(), diary.diaryId(), authorId, body, false, clock.instant());
    }

    public static Comment restore(UUID id, UUID diaryId, UUID authorId, String body, boolean hidden, Instant createdAt) {
        return new Comment(id, diaryId, authorId, body, hidden, createdAt);
    }

    /** O autor apaga o próprio comentário; o dono do diário e moderadores apagam qualquer um no diário. */
    public boolean canBeDeletedBy(UUID requesterId, CommentableDiary diary, boolean moderator) {
        return moderator || authorId.equals(requesterId) || diary.isOwnedBy(requesterId);
    }

    public boolean canBeReportedBy(UUID reporterId) {
        return !authorId.equals(reporterId);
    }

    public void registerReports(long distinctReports) {
        if (distinctReports >= HIDE_AFTER_REPORTS) {
            hidden = true;
        }
    }

    /** Um moderador revisou as denúncias e decidiu manter o comentário: ele volta a aparecer. */
    public void keepAfterReview() {
        hidden = false;
    }

    /** Texto guardado como veio (o Angular faz o escape); só removemos caracteres de controle. */
    private static String validBody(String body) {
        String cleaned = Guard.notBlank(body, "Comentário").replaceAll("[\\p{Cntrl}&&[^\n\t]]", "").strip();
        return Guard.lengthBetween(cleaned, 1, BODY_MAX_LENGTH, "Comentário");
    }

    public UUID id() {
        return id;
    }

    public UUID diaryId() {
        return diaryId;
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
