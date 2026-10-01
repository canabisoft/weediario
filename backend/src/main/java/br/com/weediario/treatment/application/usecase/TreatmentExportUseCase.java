package br.com.weediario.treatment.application.usecase;

import br.com.weediario.treatment.application.TreatmentAccess;
import br.com.weediario.treatment.application.query.TreatmentQueries;
import br.com.weediario.treatment.application.query.TreatmentQueries.BatchStock;
import br.com.weediario.treatment.application.query.TreatmentQueries.CaregiverView;
import br.com.weediario.treatment.application.query.TreatmentQueries.DoseEntry;
import br.com.weediario.treatment.application.query.TreatmentQueries.PatientSummary;
import br.com.weediario.treatment.application.query.TreatmentQueries.RatingEntry;
import br.com.weediario.treatment.application.query.TreatmentQueries.SideEffectView;
import br.com.weediario.treatment.application.query.TreatmentQueries.SymptomView;
import br.com.weediario.treatment.domain.model.TreatmentConsent;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.AccessRole;
import br.com.weediario.treatment.domain.repository.TreatmentRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Portabilidade (LGPD, art. 18, V): tudo o que a conta registrou nos perfis de que é responsável, em JSON.
 */
public final class TreatmentExportUseCase {

    private final TreatmentRepository repository;
    private final TreatmentQueries queries;
    private final TreatmentAccess access;
    private final Clock clock;

    public TreatmentExportUseCase(TreatmentRepository repository, TreatmentQueries queries, TreatmentAccess access,
                                  Clock clock) {
        this.repository = repository;
        this.queries = queries;
        this.access = access;
        this.clock = clock;
    }

    public TreatmentExport export(UUID userId) {
        access.requireConsent(userId);
        TreatmentConsent consent = repository.consentOf(userId).orElseThrow();
        Instant until = clock.instant().plus(Duration.ofDays(1));
        List<PatientExport> patients = queries.patientsOf(userId).stream()
                .filter(patient -> patient.role() == AccessRole.OWNER)
                .map(patient -> new PatientExport(patient, queries.caregivers(patient.id()),
                        queries.symptoms(patient.id(), true),
                        queries.batches(patient.id(), true, until),
                        queries.doses(patient.id(), Instant.EPOCH, until),
                        queries.checkins(patient.id(), Instant.EPOCH, until),
                        queries.looseSideEffects(patient.id(), Instant.EPOCH, until)))
                .toList();
        return new TreatmentExport(clock.instant(), consent.termsVersion(), consent.acceptedAt(), patients);
    }

    public record TreatmentExport(Instant exportedAt, String termsVersion, Instant consentAcceptedAt,
                                  List<PatientExport> patients) {
    }

    public record PatientExport(PatientSummary patient, List<CaregiverView> caregivers, List<SymptomView> symptoms,
                                List<BatchStock> medicines, List<DoseEntry> doses, List<RatingEntry> checkins,
                                List<SideEffectView> sideEffectsWithoutDose) {
    }
}
