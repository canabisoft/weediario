package br.com.weediario.treatment.application.query;

import br.com.weediario.treatment.domain.model.MedicineForm;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.AccessRole;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.DoseUnit;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Intensity;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Origin;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.RatingMoment;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Route;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.SideEffect;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.SymptomKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Leitura do acompanhamento, já com os textos decifrados. Só é chamada depois da checagem de acesso.
 */
public interface TreatmentQueries {

    List<PatientSummary> patientsOf(UUID userId);

    List<InviteView> pendingInvites(UUID userId);

    List<CaregiverView> caregivers(UUID patientId);

    List<SymptomView> symptoms(UUID patientId, boolean includeArchived);

    /** @param usageSince início da janela usada para medir o consumo recente (estimativa de duração do estoque) */
    List<BatchStock> batches(UUID patientId, boolean includeArchived, Instant usageSince);

    /** Doses do período, mais recentes primeiro, com as notas e efeitos ligados a cada uma. */
    List<DoseEntry> doses(UUID patientId, Instant from, Instant to);

    /** Notas de check-in (sem dose) do período. */
    List<RatingEntry> checkins(UUID patientId, Instant from, Instant to);

    /** Efeitos registrados sem dose ligada. */
    List<SideEffectView> looseSideEffects(UUID patientId, Instant from, Instant to);

    /**
     * @param role  papel de quem está vendo
     * @param owner apelido da conta responsável pelo perfil
     */
    record PatientSummary(UUID id, String name, String notes, AccessRole role, String owner, long caregivers) {
    }

    record InviteView(UUID patientId, String patientName, String owner, Instant invitedAt) {
    }

    record CaregiverView(String username, boolean accepted, Instant invitedAt) {
    }

    record SymptomView(UUID id, String name, SymptomKind kind, boolean archived) {
    }

    /**
     * @param remaining    saldo = inicial − doses ± ajustes, na unidade do estoque
     * @param usedRecently quanto saiu do estoque desde {@code usageSince}
     */
    record BatchStock(UUID id, String name, MedicineForm form, Origin origin, BigDecimal thc, BigDecimal cbd,
                      BigDecimal dropsPerMl, BigDecimal initialQuantity, BigDecimal remaining, BigDecimal usedRecently,
                      LocalDate manufacturedOn, LocalDate expiresOn, String notes, boolean archived) {
    }

    record DoseEntry(UUID id, UUID batchId, String batchName, MedicineForm form, BigDecimal amount, DoseUnit unit,
                     Route route, Instant takenAt, BigDecimal stockQuantity, BigDecimal thcMg, BigDecimal cbdMg,
                     String notes, String recordedBy, List<RatingEntry> ratings, List<SideEffectView> sideEffects) {
    }

    record RatingEntry(UUID id, UUID doseId, UUID symptomId, String symptom, SymptomKind kind, RatingMoment moment,
                       int value, Instant recordedAt, String recordedBy) {
    }

    record SideEffectView(UUID id, UUID doseId, SideEffect effect, Intensity intensity, Instant recordedAt) {
    }
}
