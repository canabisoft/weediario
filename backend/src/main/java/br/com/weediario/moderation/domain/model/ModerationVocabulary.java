package br.com.weediario.moderation.domain.model;

/** Vocabulário da moderação de conteúdo (Termos de Uso). */
public final class ModerationVocabulary {

    private ModerationVocabulary() {
    }

    /** O que pode ser denunciado ou retido. */
    public enum TargetType {
        COMMENT,
        DIARY,
        REVIEW,
        RECIPE,
        RECIPE_COMMENT,
        PROFILE
    }

    /** Motivo escolhido por quem denuncia. */
    public enum ReportReason {
        ILLEGAL_COMMERCE,
        TRAFFICKING_APOLOGY,
        INAPPROPRIATE,
        SPAM,
        OTHER
    }

    /** Motivo registrado pela moderação no log (os da denúncia, mais "improcedente"). */
    public enum ModerationReason {
        ILLEGAL_COMMERCE,
        TRAFFICKING_APOLOGY,
        INAPPROPRIATE,
        SPAM,
        OTHER,
        UNFOUNDED,
        /** Mudança na equipe (promoção a moderador ou saída da equipe), feita por um admin. */
        STAFF_CHANGE
    }

    public enum HoldCause {
        /** Atingiu o limite de denúncias distintas. */
        REPORTS,
        /** O filtro anti-comércio segurou na hora de publicar. */
        AUTO_FILTER
    }

    public enum ModerationAction {
        CONTENT_REMOVED,
        CONTENT_RESTORED,
        USER_SUSPENDED,
        USER_BANNED,
        USER_REINSTATED,
        ROLE_GRANTED,
        ROLE_REVOKED
    }

    /** Decisão sobre o conteúdo. */
    public enum Verdict {
        /** Denúncia improcedente: volta a aparecer. */
        KEEP,
        REMOVE
    }

    /** Decisão sobre a conta de quem publicou (junto com a remoção). */
    public enum AccountAction {
        NONE,
        SUSPEND,
        BAN
    }
}
