package br.com.weediario.treatment.application.usecase;

import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.treatment.domain.model.TreatmentConsent;
import br.com.weediario.treatment.domain.repository.TreatmentRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Consentimento para dados de saúde. Revogar apaga de verdade tudo o que a conta tem no módulo.
 */
public final class TreatmentConsentUseCase {

    private final TreatmentRepository repository;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public TreatmentConsentUseCase(TreatmentRepository repository, UnitOfWork unitOfWork, Clock clock) {
        this.repository = repository;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public ConsentStatus status(UUID userId) {
        Optional<TreatmentConsent> consent = repository.consentOf(userId);
        return new ConsentStatus(consent.map(TreatmentConsent::current).orElse(false), TreatmentConsent.CURRENT_TERMS,
                consent.map(TreatmentConsent::termsVersion).orElse(null),
                consent.map(TreatmentConsent::acceptedAt).orElse(null));
    }

    public ConsentStatus accept(UUID userId, String termsVersion) {
        unitOfWork.run(() -> repository.saveConsent(TreatmentConsent.accept(userId, termsVersion, clock)));
        return status(userId);
    }

    /**
     * Revoga o consentimento e apaga os perfis de que a conta é responsável (com doses, notas, remédios e
     * cuidadores) e os acessos dela como cuidadora de perfis de outras contas.
     */
    public void revokeAndErase(UUID userId) {
        unitOfWork.run(() -> {
            repository.deletePatientsOwnedBy(userId);
            repository.leaveAllCaregiving(userId);
            repository.deleteConsent(userId);
        });
    }

    /**
     * @param accepted        se há consentimento na versão atual do termo
     * @param acceptedVersion versão aceita antes (pode ser antiga)
     */
    public record ConsentStatus(boolean accepted, String currentVersion, String acceptedVersion, Instant acceptedAt) {
    }
}
