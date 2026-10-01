package br.com.weediario.treatment.application;

import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.treatment.domain.model.Patient;
import br.com.weediario.treatment.domain.model.TreatmentConsent;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.AccessRole;
import br.com.weediario.treatment.domain.repository.TreatmentRepository;

import java.util.UUID;

/**
 * Quem pode ver e registrar o quê. Sem consentimento vigente, nada do módulo é acessível. Perfil de outra pessoa
 * responde como inexistente (404), para não revelar que existe.
 */
public final class TreatmentAccess {

    private final TreatmentRepository repository;

    public TreatmentAccess(TreatmentRepository repository) {
        this.repository = repository;
    }

    public void requireConsent(UUID userId) {
        if (repository.consentOf(userId).filter(TreatmentConsent::current).isEmpty()) {
            throw new ConsentRequiredException();
        }
    }

    /** Dono ou cuidador com convite aceito. */
    public AccessRole roleOn(UUID patientId, UUID userId) {
        requireConsent(userId);
        return repository.accessOf(patientId, userId).orElseThrow(() -> new ResourceNotFoundException("Paciente"));
    }

    public Patient requireOwner(UUID patientId, UUID userId) {
        if (roleOn(patientId, userId) != AccessRole.OWNER) {
            throw new DomainException("Só a conta responsável pelo perfil pode fazer isso");
        }
        return repository.findPatient(patientId).orElseThrow(() -> new ResourceNotFoundException("Paciente"));
    }
}
