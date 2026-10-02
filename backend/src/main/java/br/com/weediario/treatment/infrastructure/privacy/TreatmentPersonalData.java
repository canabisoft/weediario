package br.com.weediario.treatment.infrastructure.privacy;

import br.com.weediario.shared.application.PersonalDataSection;
import br.com.weediario.treatment.application.usecase.TreatmentExportUseCase;
import br.com.weediario.treatment.domain.repository.TreatmentRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Acompanhamento de tratamento, pela mesma exportação da tela de tratamento (os textos de saúde saem decifrados). Só
 * entram os pacientes de quem a pessoa é titular. Ao apagar a conta, os pacientes dela somem junto (cascade), e quem
 * era cuidador perde o acesso; o que a pessoa registrou como cuidadora de outro paciente fica, sem autor.
 */
@Component
public class TreatmentPersonalData implements PersonalDataSection {

    private final TreatmentRepository repository;
    private final TreatmentExportUseCase export;

    public TreatmentPersonalData(TreatmentRepository repository, TreatmentExportUseCase export) {
        this.repository = repository;
        this.export = export;
    }

    @Override
    public String name() {
        return "tratamento";
    }

    @Override
    public Object export(UUID userId) {
        // Sem consentimento, não há dado de tratamento (e a exportação exige o consentimento).
        return repository.consentOf(userId).isPresent() ? export.export(userId) : null;
    }
}
