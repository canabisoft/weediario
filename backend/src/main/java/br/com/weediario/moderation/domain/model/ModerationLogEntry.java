package br.com.weediario.moderation.domain.model;

import br.com.weediario.moderation.domain.model.ModerationVocabulary.ModerationAction;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.ModerationReason;
import br.com.weediario.shared.domain.Guard;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de uma ação da moderação, para demonstrar diligência se a plataforma for questionada.
 * Só inserção. Sem IP, sem quem denunciou e sem o conteúdo: no lugar dele, o hash SHA-256 do texto removido
 * (prova qual era o conteúdo sem guardá-lo).
 *
 * @param reportedUsername apelido na data da ação (a conta pode ser apagada depois)
 */
public record ModerationLogEntry(UUID id, ContentRef target, UUID reportedUserId, String reportedUsername,
                                 UUID moderatorId, ModerationAction action, ModerationReason reason, String note,
                                 String contentSha256, Instant createdAt) {

    public static final int NOTE_MAX_LENGTH = 500;
    /** Prazo de guarda: defesa em processo administrativo ou judicial (LGPD, art. 7º, VI). */
    public static final int RETENTION_YEARS = 5;

    public ModerationLogEntry {
        Guard.notNull(id, "Id");
        Guard.notNull(target, "Conteúdo");
        Guard.notNull(action, "Ação");
        Guard.notNull(reason, "Motivo");
        Guard.notNull(createdAt, "Data");
        note = note == null || note.isBlank() ? null : Guard.lengthBetween(note.strip(), 1, NOTE_MAX_LENGTH, "Observação");
    }

    public static ModerationLogEntry of(ContentRef target, UUID reportedUserId, String reportedUsername, UUID moderatorId,
                                        ModerationAction action, ModerationReason reason, String note,
                                        String contentSha256, Instant at) {
        return new ModerationLogEntry(UUID.randomUUID(), target, reportedUserId, reportedUsername, moderatorId, action,
                reason, note, contentSha256, at);
    }
}
