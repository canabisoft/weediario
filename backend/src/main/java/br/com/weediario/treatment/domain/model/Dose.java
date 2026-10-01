package br.com.weediario.treatment.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;
import br.com.weediario.treatment.domain.model.MedicineBatch.DoseMeasure;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.DoseUnit;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Route;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro de um uso que <strong>já aconteceu</strong>. Nunca é bloqueado por lote vencido ou estoque zerado:
 * o histórico precisa refletir o que o paciente de fato usou (os avisos ficam na tela).
 *
 * @param recordedBy conta que registrou (o próprio paciente ou um cuidador)
 */
public record Dose(UUID id, UUID patientId, UUID batchId, BigDecimal amount, DoseUnit unit, Route route,
                   Instant takenAt, DoseMeasure measure, String notes, UUID recordedBy, Instant createdAt) {

    public static final int NOTES_MAX_LENGTH = 500;
    /** Tolerância para relógio do celular um pouco adiantado. */
    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(10);

    public Dose {
        Guard.notNull(id, "Id");
        Guard.notNull(patientId, "Paciente");
        Guard.notNull(batchId, "Remédio");
        Guard.notNull(amount, "Quantidade");
        Guard.notNull(unit, "Unidade");
        Guard.notNull(route, "Via");
        Guard.notNull(takenAt, "Horário");
        Guard.notNull(measure, "Medida");
        notes = MedicineBatch.optionalText(notes, NOTES_MAX_LENGTH, "Observações");
        Guard.notNull(createdAt, "Data de registro");
    }

    public static Dose record(MedicineBatch batch, BigDecimal amount, DoseUnit unit, Route route, Instant takenAt,
                              String notes, UUID recordedBy, Clock clock) {
        Instant now = clock.instant();
        Instant when = takenAt == null ? now : takenAt;
        if (when.isAfter(now.plus(FUTURE_TOLERANCE))) {
            throw new DomainException("Registre só usos que já aconteceram");
        }
        if (batch.archived()) {
            throw new DomainException("Este remédio foi arquivado");
        }
        return new Dose(UUID.randomUUID(), batch.patientId(), batch.id(), amount, unit,
                route == null ? batch.form().usualRoute() : route, when, batch.measure(amount, unit), notes,
                recordedBy, now);
    }
}
