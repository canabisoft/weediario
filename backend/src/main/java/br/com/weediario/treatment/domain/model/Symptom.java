package br.com.weediario.treatment.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.SymptomKind;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Sintoma ou evento acompanhado pelo paciente (ex.: dor lombar, ansiedade, crises).
 */
public record Symptom(UUID id, UUID patientId, String name, SymptomKind kind, boolean archived, Instant createdAt) {

    public static final int NAME_MAX_LENGTH = 60;
    public static final int MAX_ACTIVE = 12;
    public static final int SCALE_MAX = 10;
    public static final int COUNT_MAX = 1000;

    public Symptom {
        Guard.notNull(id, "Id");
        Guard.notNull(patientId, "Paciente");
        name = Guard.lengthBetween(Guard.notBlank(name, "Nome").strip(), 1, NAME_MAX_LENGTH, "Nome");
        Guard.notNull(kind, "Tipo");
        Guard.notNull(createdAt, "Data de criação");
    }

    public static Symptom track(UUID patientId, String name, SymptomKind kind, Clock clock) {
        return new Symptom(UUID.randomUUID(), patientId, name, kind, false, clock.instant());
    }

    public Symptom archive(boolean value) {
        return new Symptom(id, patientId, name, kind, value, createdAt);
    }

    /** Escala de 0 (sem sintoma) a 10 (o pior possível) ou contagem de eventos. */
    public int validValue(int value) {
        int max = kind == SymptomKind.SCALE ? SCALE_MAX : COUNT_MAX;
        if (value < 0 || value > max) {
            throw new DomainException(kind == SymptomKind.SCALE
                    ? "A nota de \"" + name + "\" vai de 0 a 10"
                    : "Contagem de \"" + name + "\" inválida");
        }
        return value;
    }
}
