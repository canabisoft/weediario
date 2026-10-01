package br.com.weediario.treatment.domain.repository;

import br.com.weediario.treatment.domain.model.Dose;
import br.com.weediario.treatment.domain.model.MedicineBatch;
import br.com.weediario.treatment.domain.model.Patient;
import br.com.weediario.treatment.domain.model.SideEffectEntry;
import br.com.weediario.treatment.domain.model.StockAdjustment;
import br.com.weediario.treatment.domain.model.Symptom;
import br.com.weediario.treatment.domain.model.SymptomRating;
import br.com.weediario.treatment.domain.model.TreatmentConsent;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.AccessRole;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência do acompanhamento. Textos livres (nomes, observações) são cifrados pela implementação antes de
 * chegar ao banco.
 */
public interface TreatmentRepository {

    // ------------------------------------------------------------------------------- consentimento

    Optional<TreatmentConsent> consentOf(UUID userId);

    void saveConsent(TreatmentConsent consent);

    void deleteConsent(UUID userId);

    // ---------------------------------------------------------------------------- pacientes e acesso

    Patient savePatient(Patient patient);

    Optional<Patient> findPatient(UUID id);

    long countPatientsOwnedBy(UUID ownerId);

    void deletePatient(UUID id);

    /** Apaga de verdade todos os perfis da conta (e, em cascata, tudo o que foi registrado neles). */
    void deletePatientsOwnedBy(UUID ownerId);

    /** Dono, cuidador com convite aceito, ou vazio (convite pendente não dá acesso). */
    Optional<AccessRole> accessOf(UUID patientId, UUID userId);

    /** Cria ou mantém o convite pendente (não rebaixa quem já aceitou). */
    void invite(UUID patientId, UUID userId, Instant at);

    long countCaregivers(UUID patientId);

    /** @return {@code false} se não havia convite pendente */
    boolean acceptInvite(UUID patientId, UUID userId, Instant at);

    /** Remove convite ou acesso. @return {@code false} se não havia */
    boolean removeCaregiver(UUID patientId, UUID userId);

    void leaveAllCaregiving(UUID userId);

    // ------------------------------------------------------------------------------------ sintomas

    Symptom saveSymptom(Symptom symptom);

    Optional<Symptom> findSymptom(UUID id);

    long countActiveSymptoms(UUID patientId);

    // ------------------------------------------------------------------------------ remédios e estoque

    MedicineBatch saveBatch(MedicineBatch batch);

    Optional<MedicineBatch> findBatch(UUID id);

    void saveAdjustment(StockAdjustment adjustment);

    // -------------------------------------------------------------------------------------- registros

    Dose saveDose(Dose dose);

    Optional<Dose> findDose(UUID id);

    void deleteDose(UUID id);

    /** Uma nota por dose, sintoma e momento: registrar de novo corrige a anterior. */
    void saveRating(SymptomRating rating);

    void saveSideEffect(SideEffectEntry entry);

    Optional<SideEffectEntry> findSideEffect(UUID id);

    void deleteSideEffect(UUID id);

    void deleteRating(UUID id, UUID patientId);
}
