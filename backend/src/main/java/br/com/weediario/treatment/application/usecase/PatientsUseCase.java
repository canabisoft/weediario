package br.com.weediario.treatment.application.usecase;

import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.treatment.application.TreatmentAccess;
import br.com.weediario.treatment.application.port.TreatmentUsers;
import br.com.weediario.treatment.application.query.TreatmentQueries;
import br.com.weediario.treatment.application.query.TreatmentQueries.CaregiverView;
import br.com.weediario.treatment.application.query.TreatmentQueries.InviteView;
import br.com.weediario.treatment.application.query.TreatmentQueries.PatientSummary;
import br.com.weediario.treatment.application.query.TreatmentQueries.SymptomView;
import br.com.weediario.treatment.domain.model.Patient;
import br.com.weediario.treatment.domain.model.Symptom;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.SymptomKind;
import br.com.weediario.treatment.domain.repository.TreatmentRepository;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Perfis de paciente, cuidadores e sintomas acompanhados.
 * <p>
 * A conta responsável cria perfis (para si ou para um dependente) e convida outras contas como cuidadoras.
 * O convite só vale depois de aceito, e quem aceita precisa ter dado o próprio consentimento.
 */
public final class PatientsUseCase {

    private final TreatmentRepository repository;
    private final TreatmentQueries queries;
    private final TreatmentAccess access;
    private final TreatmentUsers users;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public PatientsUseCase(TreatmentRepository repository, TreatmentQueries queries, TreatmentAccess access,
                           TreatmentUsers users, UnitOfWork unitOfWork, Clock clock) {
        this.repository = repository;
        this.queries = queries;
        this.access = access;
        this.users = users;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public Overview overview(UUID userId) {
        access.requireConsent(userId);
        return new Overview(queries.patientsOf(userId), queries.pendingInvites(userId));
    }

    public UUID create(UUID ownerId, String name, String notes) {
        access.requireConsent(ownerId);
        return unitOfWork.execute(() -> {
            if (repository.countPatientsOwnedBy(ownerId) >= Patient.MAX_PER_OWNER) {
                throw new DomainException("Limite de " + Patient.MAX_PER_OWNER + " perfis por conta");
            }
            return repository.savePatient(Patient.create(ownerId, name, notes, clock)).id();
        });
    }

    public void update(UUID patientId, UUID ownerId, String name, String notes) {
        unitOfWork.run(() -> repository.savePatient(access.requireOwner(patientId, ownerId).rename(name, notes)));
    }

    /** Apaga o perfil e tudo o que foi registrado nele. */
    public void delete(UUID patientId, UUID ownerId) {
        unitOfWork.run(() -> repository.deletePatient(access.requireOwner(patientId, ownerId).id()));
    }

    public List<CaregiverView> caregivers(UUID patientId, UUID userId) {
        access.roleOn(patientId, userId);
        return queries.caregivers(patientId);
    }

    public void invite(UUID patientId, UUID ownerId, String username) {
        unitOfWork.run(() -> {
            access.requireOwner(patientId, ownerId);
            UUID caregiver = users.activeUserId(username == null ? "" : username.strip())
                    .orElseThrow(() -> new ResourceNotFoundException("Grower"));
            if (caregiver.equals(ownerId)) {
                throw new DomainException("Você já é a conta responsável por este perfil");
            }
            if (repository.countCaregivers(patientId) >= Patient.MAX_CAREGIVERS) {
                throw new DomainException("Limite de " + Patient.MAX_CAREGIVERS + " cuidadores por perfil");
            }
            repository.invite(patientId, caregiver, clock.instant());
        });
    }

    public void acceptInvite(UUID patientId, UUID userId) {
        access.requireConsent(userId);
        unitOfWork.run(() -> {
            if (!repository.acceptInvite(patientId, userId, clock.instant())) {
                throw new ResourceNotFoundException("Convite");
            }
        });
    }

    /** Recusar um convite ou deixar de ser cuidador. Não exige consentimento: sair sempre é possível. */
    public void leave(UUID patientId, UUID userId) {
        unitOfWork.run(() -> {
            if (!repository.removeCaregiver(patientId, userId)) {
                throw new ResourceNotFoundException("Convite");
            }
        });
    }

    public void removeCaregiver(UUID patientId, UUID ownerId, String username) {
        unitOfWork.run(() -> {
            access.requireOwner(patientId, ownerId);
            UUID caregiver = users.activeUserId(username).orElseThrow(() -> new ResourceNotFoundException("Cuidador"));
            if (!repository.removeCaregiver(patientId, caregiver)) {
                throw new ResourceNotFoundException("Cuidador");
            }
        });
    }

    // ------------------------------------------------------------------------------------ sintomas

    public List<SymptomView> symptoms(UUID patientId, UUID userId, boolean includeArchived) {
        access.roleOn(patientId, userId);
        return queries.symptoms(patientId, includeArchived);
    }

    public UUID addSymptom(UUID patientId, UUID userId, String name, SymptomKind kind) {
        return unitOfWork.execute(() -> {
            access.roleOn(patientId, userId);
            if (repository.countActiveSymptoms(patientId) >= Symptom.MAX_ACTIVE) {
                throw new DomainException("Limite de " + Symptom.MAX_ACTIVE + " sintomas acompanhados ao mesmo tempo");
            }
            return repository.saveSymptom(Symptom.track(patientId, name, kind == null ? SymptomKind.SCALE : kind, clock))
                    .id();
        });
    }

    /** Arquivar mantém o histórico; o sintoma só sai das telas de registro. */
    public void archiveSymptom(UUID patientId, UUID symptomId, UUID userId, boolean archived) {
        unitOfWork.run(() -> {
            access.roleOn(patientId, userId);
            Symptom symptom = repository.findSymptom(symptomId)
                    .filter(s -> s.patientId().equals(patientId))
                    .orElseThrow(() -> new ResourceNotFoundException("Sintoma"));
            if (!archived && symptom.archived() && repository.countActiveSymptoms(patientId) >= Symptom.MAX_ACTIVE) {
                throw new DomainException("Limite de " + Symptom.MAX_ACTIVE + " sintomas acompanhados ao mesmo tempo");
            }
            repository.saveSymptom(symptom.archive(archived));
        });
    }

    /** @param invites convites para cuidar de perfis de outras contas, esperando resposta */
    public record Overview(List<PatientSummary> patients, List<InviteView> invites) {
    }
}
