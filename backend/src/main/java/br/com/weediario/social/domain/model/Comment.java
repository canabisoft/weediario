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
 * <p>
 * Respostas têm um nível só: {@code parentId} aponta sempre para um comentário raiz do mesmo diário. Responder uma
 * resposta anexa a nova resposta ao mesmo comentário raiz (a conversa continua legível no celular e ninguém recebe
 * erro por clicar em "Responder"). Apagar o comentário raiz apaga as respostas junto.
 */
public final class Comment {

    public static final int BODY_MAX_LENGTH = 2000;

    private final UUID id;
    private final UUID diaryId;
    private final UUID parentId;
    private final UUID authorId;
    private final String body;
    private boolean hidden;
    private final Instant createdAt;

    private Comment(UUID id, UUID diaryId, UUID parentId, UUID authorId, String body, boolean hidden,
                    Instant createdAt) {
        this.id = Guard.notNull(id, "Id");
        this.diaryId = Guard.notNull(diaryId, "Diário");
        if (id.equals(parentId)) {
            throw new DomainException("Um comentário não responde a si mesmo");
        }
        this.parentId = parentId;
        this.authorId = Guard.notNull(authorId, "Autor");
        this.body = validBody(body);
        this.hidden = hidden;
        this.createdAt = Guard.notNull(createdAt, "Data de criação");
    }

    public static Comment post(CommentableDiary diary, UUID authorId, String body, Clock clock) {
        if (!diary.isVisibleTo(Optional.of(authorId))) {
            throw new DomainException("Diário não encontrado");
        }
        return new Comment(UUID.randomUUID(), diary.diaryId(), null, authorId, body, false, clock.instant());
    }

    /**
     * Resposta a {@code answered}. Se ele já for uma resposta, a nova fica no mesmo comentário raiz (um nível só).
     */
    public static Comment reply(CommentableDiary diary, Comment answered, UUID authorId, String body, Clock clock) {
        if (!diary.isVisibleTo(Optional.of(authorId)) || !answered.diaryId.equals(diary.diaryId())) {
            throw new DomainException("Comentário não encontrado");
        }
        return new Comment(UUID.randomUUID(), diary.diaryId(), answered.rootId(), authorId, body, false,
                clock.instant());
    }

    public static Comment restore(UUID id, UUID diaryId, UUID parentId, UUID authorId, String body, boolean hidden,
                                  Instant createdAt) {
        return new Comment(id, diaryId, parentId, authorId, body, hidden, createdAt);
    }

    /** Comentário raiz da conversa: ele mesmo ou aquele a que responde. */
    public UUID rootId() {
        return parentId == null ? id : parentId;
    }

    public boolean isReply() {
        return parentId != null;
    }

    /** Oculto (retido ou denunciado) só aparece para quem escreveu. */
    public boolean isVisibleTo(UUID viewerId) {
        return !hidden || authorId.equals(viewerId);
    }

    /**
     * O autor apaga o próprio comentário e o dono do diário apaga os do diário. A moderação não apaga por aqui: remove
     * pela decisão de moderação, que fica no log e respeita a hierarquia da equipe.
     */
    public boolean canBeDeletedBy(UUID requesterId, CommentableDiary diary) {
        return authorId.equals(requesterId) || diary.isOwnedBy(requesterId);
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
