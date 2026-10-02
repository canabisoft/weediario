package br.com.weediario.catalog.infrastructure.config;

import br.com.weediario.catalog.application.query.BrowseCatalogUseCase;
import br.com.weediario.catalog.application.query.CatalogQueries;
import br.com.weediario.catalog.application.usecase.BreederProgramUseCase;
import br.com.weediario.catalog.application.usecase.CurateCatalogUseCase;
import br.com.weediario.catalog.application.usecase.ImportDatasetUseCase;
import br.com.weediario.catalog.application.usecase.PromoteTestingStrainsUseCase;
import br.com.weediario.catalog.application.usecase.ResolveCatalogUseCase;
import br.com.weediario.catalog.domain.repository.BreederRepresentativeRepository;
import br.com.weediario.catalog.domain.repository.CatalogAuditLog;
import br.com.weediario.catalog.domain.repository.CatalogRepository;
import br.com.weediario.shared.application.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;

@Configuration
public class CatalogConfig {

    @Bean
    ResolveCatalogUseCase resolveCatalogUseCase(CatalogRepository catalog, UnitOfWork unitOfWork, Clock clock) {
        return new ResolveCatalogUseCase(catalog, unitOfWork, clock);
    }

    @Bean
    CurateCatalogUseCase curateCatalogUseCase(CatalogRepository catalog, CatalogAuditLog audit, UnitOfWork unitOfWork,
                                              Clock clock) {
        return new CurateCatalogUseCase(catalog, audit, unitOfWork, clock);
    }

    @Bean
    ImportDatasetUseCase importDatasetUseCase(CatalogRepository catalog, UnitOfWork unitOfWork, Clock clock) {
        return new ImportDatasetUseCase(catalog, unitOfWork, clock);
    }

    @Bean
    BrowseCatalogUseCase browseCatalogUseCase(CatalogQueries queries) {
        return new BrowseCatalogUseCase(queries);
    }

    @Bean
    BreederProgramUseCase breederProgramUseCase(CatalogRepository catalog,
                                                BreederRepresentativeRepository representatives,
                                                CatalogAuditLog audit, UnitOfWork unitOfWork, Clock clock) {
        return new BreederProgramUseCase(catalog, representatives, audit, unitOfWork, clock);
    }

    @Bean
    PromoteTestingStrainsUseCase promoteTestingStrainsUseCase(CatalogRepository catalog, UnitOfWork unitOfWork,
                                                              Clock clock) {
        return new PromoteTestingStrainsUseCase(catalog, unitOfWork, clock);
    }

    @Bean
    TestingStrainsJob testingStrainsJob(PromoteTestingStrainsUseCase promote) {
        return new TestingStrainsJob(promote);
    }

    /**
     * Rede de segurança da promoção: a review já dispara a checagem na hora, mas um diário novo (o terceiro
     * cultivador) ou uma review liberada pela moderação também podem completar o teste.
     */
    static class TestingStrainsJob {

        private static final Logger log = LoggerFactory.getLogger(TestingStrainsJob.class);

        private final PromoteTestingStrainsUseCase promote;

        TestingStrainsJob(PromoteTestingStrainsUseCase promote) {
            this.promote = promote;
        }

        @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT5M")
        void promoteReady() {
            int promoted = promote.promoteAllReady();
            if (promoted > 0) {
                log.info("{} semente(s) em teste entraram no banco oficial", promoted);
            }
        }
    }

    @Bean
    CatalogAuditJanitor catalogAuditJanitor(CatalogAuditLog audit, Clock clock) {
        return new CatalogAuditJanitor(audit, clock);
    }

    /** Minimização: o registro da curadoria some depois de {@link CatalogAuditLog#RETENTION_YEARS} anos. */
    static class CatalogAuditJanitor {

        private final CatalogAuditLog audit;
        private final Clock clock;

        CatalogAuditJanitor(CatalogAuditLog audit, Clock clock) {
            this.audit = audit;
            this.clock = clock;
        }

        @Scheduled(fixedDelayString = "PT24H", initialDelayString = "PT30M")
        @org.springframework.transaction.annotation.Transactional
        void purge() {
            audit.deleteBefore(java.time.ZonedDateTime.now(clock).minusYears(CatalogAuditLog.RETENTION_YEARS).toInstant());
        }
    }
}
