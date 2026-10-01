package br.com.weediario.treatment.infrastructure.config;

import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.treatment.application.TreatmentAccess;
import br.com.weediario.treatment.application.port.ReportRenderer;
import br.com.weediario.treatment.application.port.TreatmentUsers;
import br.com.weediario.treatment.application.query.TreatmentQueries;
import br.com.weediario.treatment.application.usecase.MedicinesUseCase;
import br.com.weediario.treatment.application.usecase.PatientsUseCase;
import br.com.weediario.treatment.application.usecase.TreatmentConsentUseCase;
import br.com.weediario.treatment.application.usecase.TreatmentExportUseCase;
import br.com.weediario.treatment.application.usecase.TreatmentLogUseCase;
import br.com.weediario.treatment.application.usecase.TreatmentReportUseCase;
import br.com.weediario.treatment.domain.repository.TreatmentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TreatmentConfig {

    @Bean
    TreatmentAccess treatmentAccess(TreatmentRepository repository) {
        return new TreatmentAccess(repository);
    }

    @Bean
    TreatmentConsentUseCase treatmentConsentUseCase(TreatmentRepository repository, UnitOfWork unitOfWork, Clock clock) {
        return new TreatmentConsentUseCase(repository, unitOfWork, clock);
    }

    @Bean
    PatientsUseCase patientsUseCase(TreatmentRepository repository, TreatmentQueries queries, TreatmentAccess access,
                                    TreatmentUsers users, UnitOfWork unitOfWork, Clock clock) {
        return new PatientsUseCase(repository, queries, access, users, unitOfWork, clock);
    }

    @Bean
    MedicinesUseCase medicinesUseCase(TreatmentRepository repository, TreatmentQueries queries, TreatmentAccess access,
                                      UnitOfWork unitOfWork, Clock clock) {
        return new MedicinesUseCase(repository, queries, access, unitOfWork, clock);
    }

    @Bean
    TreatmentLogUseCase treatmentLogUseCase(TreatmentRepository repository, TreatmentQueries queries,
                                            TreatmentAccess access, MedicinesUseCase medicines, UnitOfWork unitOfWork,
                                            Clock clock) {
        return new TreatmentLogUseCase(repository, queries, access, medicines, unitOfWork, clock);
    }

    @Bean
    TreatmentReportUseCase treatmentReportUseCase(TreatmentQueries queries, TreatmentAccess access,
                                                  TreatmentLogUseCase log, ReportRenderer renderer, Clock clock) {
        return new TreatmentReportUseCase(queries, access, log, renderer, clock);
    }

    @Bean
    TreatmentExportUseCase treatmentExportUseCase(TreatmentRepository repository, TreatmentQueries queries,
                                                  TreatmentAccess access, Clock clock) {
        return new TreatmentExportUseCase(repository, queries, access, clock);
    }
}
