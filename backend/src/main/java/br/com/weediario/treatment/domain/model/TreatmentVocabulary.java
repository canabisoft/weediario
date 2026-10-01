package br.com.weediario.treatment.domain.model;

/**
 * Vocabulário do acompanhamento de tratamento. Só descreve o que o paciente usou e sentiu; nenhum valor aqui
 * representa dose recomendada.
 */
public final class TreatmentVocabulary {

    private TreatmentVocabulary() {
    }

    /** Unidade em que o estoque de um lote é contado. */
    public enum StockUnit {
        ML,
        G,
        UNIT
    }

    /** Como a concentração vem no rótulo/laudo de cada forma farmacêutica. */
    public enum ConcentrationUnit {
        MG_PER_ML,
        PERCENT,
        MG_PER_UNIT,
        MG_PER_G
    }

    /** Unidade em que o paciente informa quanto usou. */
    public enum DoseUnit {
        DROP,
        ML,
        G,
        UNIT,
        /** Aplicação de tópico sem peso conhecido: registrada, mas não desconta estoque nem gera mg. */
        APPLICATION
    }

    public enum Route {
        SUBLINGUAL,
        ORAL,
        INHALED,
        TOPICAL,
        OTHER
    }

    public enum Origin {
        HOME_GROWN,
        ASSOCIATION,
        PHARMACY,
        IMPORTED,
        OTHER
    }

    /** Escala 0–10 (dor, ansiedade...) ou contagem de eventos (crises, despertares). */
    public enum SymptomKind {
        SCALE,
        COUNT
    }

    public enum RatingMoment {
        BEFORE,
        AFTER,
        CHECKIN
    }

    public enum SideEffect {
        DIZZINESS,
        DROWSINESS,
        DRY_MOUTH,
        RED_EYES,
        TACHYCARDIA,
        ANXIETY,
        HUNGER,
        NAUSEA,
        HEADACHE,
        OTHER
    }

    public enum Intensity {
        MILD,
        MODERATE,
        SEVERE
    }

    public enum StockAdjustmentKind {
        /** Correção de contagem (positiva ou negativa). */
        ADJUSTMENT,
        /** Perda, vencimento, quebra: sempre negativa. */
        DISCARD
    }

    /** Dono do perfil (cria, apaga, convida) ou cuidador convidado (registra e consulta). */
    public enum AccessRole {
        OWNER,
        CAREGIVER
    }
}
