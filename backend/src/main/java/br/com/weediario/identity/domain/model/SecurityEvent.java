package br.com.weediario.identity.domain.model;

import java.time.Instant;

/**
 * Mudança sensível na conta, mostrada ao próprio dono (e avisada por e-mail, se houver) para ele perceber uma invasão.
 * Deliberadamente sem IP, aparelho ou local: só o que aconteceu e quando. Guardado por {@link #RETENTION_DAYS} dias.
 */
public record SecurityEvent(UserId userId, Kind kind, Instant occurredAt) {

    public static final int RETENTION_DAYS = 90;

    public enum Kind {
        PASSWORD_CHANGED("Senha alterada"),
        PASSWORD_RESET_BY_PHRASE("Senha redefinida com a frase de recuperação"),
        PASSWORD_RESET_BY_EMAIL("Senha redefinida pelo link do e-mail"),
        RECOVERY_PHRASE_CHANGED("Frase de recuperação trocada"),
        EMAIL_ADDED("E-mail cadastrado"),
        EMAIL_CHANGED("E-mail alterado"),
        EMAIL_REMOVED("E-mail removido"),
        EMAIL_VERIFIED("E-mail confirmado");

        private final String description;

        Kind(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }
}
