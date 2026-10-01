package br.com.weediario.treatment.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.RatingMoment;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Nota de um sintoma: antes ou depois de uma dose, ou num check-in sem dose.
 */
public record SymptomRating(UUID id, UUID patientId, UUID symptomId, UUID doseId, RatingMoment moment, int value,
                            Instant recordedAt, UUID recordedBy) {

    public SymptomRating {
        Guard.notNull(id, "Id");
        Guard.notNull(patientId, "Paciente");
        Guard.notNull(symptomId, "Sintoma");
        Guard.notNull(moment, "Momento");
        Guard.notNull(recordedAt, "Data");
        if ((moment == RatingMoment.CHECKIN) != (doseId == null)) {
            throw new DomainException("Notas de antes/depois pertencem a uma dose; check-in não");
        }
    }

    public static SymptomRating of(Symptom symptom, Dose dose, RatingMoment moment, int value, UUID recordedBy,
                                   Clock clock) {
        if (symptom.archived()) {
            throw new DomainException("O sintoma \"" + symptom.name() + "\" foi arquivado");
        }
        if (dose != null && !dose.patientId().equals(symptom.patientId())) {
            throw new DomainException("Sintoma e dose são de pacientes diferentes");
        }
        return new SymptomRating(UUID.randomUUID(), symptom.patientId(), symptom.id(), dose == null ? null : dose.id(),
                moment, symptom.validValue(value), clock.instant(), recordedBy);
    }
}
