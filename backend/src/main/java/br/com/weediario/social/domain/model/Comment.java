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
 * Denúncias e decisões ficam no contexto de moderação; aqui o comentário só sabe se está oculto (retido pelo
 * filtro anti-comércio ou por denúncias, aguardando a moderação).
 */
public final class Comment {

    public static final int BODY_MAX_LENGTH = 2000;

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
