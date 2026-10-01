package br.com.weediario.treatment.domain.model;

import br.com.weediario.shared.domain.Guard;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Intensity;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.SideEffect;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Efeito indesejado que o paciente percebeu, ligado a uma dose quando ele souber qual foi. */
public record SideEffectEntry(UUID id, UUID patientId, UUID doseId, SideEffect effect, Intensity intensity,
                              Instant recordedAt, UUID recordedBy) {

    public SideEffectEntry {
        Guard.notNull(id, "Id");
        Guard.notNull(patientId, "Paciente");
        Guard.notNull(effect, "Efeito");
        intensity = intensity == null ? Intensity.MILD : intensity;
        Guard.notNull(recordedAt, "Data");
    }

    public static SideEffectEntry of(UUID patientId, UUID doseId, SideEffect effect, Intensity intensity,
                                     UUID recordedBy, Clock clock) {
        return new SideEffectEntry(UUID.randomUUID(), patientId, doseId, effect, intensity, clock.instant(), recordedBy);
    }
}
