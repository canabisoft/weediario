package br.com.weediario.moderation.infrastructure.config;

import br.com.weediario.moderation.application.port.ModeratedAccounts;
import br.com.weediario.moderation.application.port.ModeratedContent;
import br.com.weediario.moderation.application.port.ReporterPseudonyms;
import br.com.weediario.moderation.application.port.ReporterStandings;
import br.com.weediario.moderation.application.query.ModerationQueries;
import br.com.weediario.moderation.application.query.PlatformMetrics;
import br.com.weediario.moderation.application.usecase.AdminMetricsUseCase;
import br.com.weediario.moderation.application.usecase.ContentHoldUseCase;
import br.com.weediario.moderation.application.usecase.ModerateContentUseCase;
import br.com.weediario.moderation.application.usecase.ManageTeamUseCase;
import br.com.weediario.moderation.application.usecase.ModerationQueueUseCase;
import br.com.weediario.moderation.application.usecase.ReportContentUseCase;
import br.com.weediario.moderation.domain.model.ModerationLogEntry;
import br.com.weediario.moderation.domain.repository.ModerationRepository;
import br.com.weediario.shared.application.ActionQuota;
import br.com.weediario.shared.application.UnitOfWork;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZonedDateTime;

@Configuration
public class ModerationConfig {

    @Bean
    ReportContentUseCase reportContentUseCase(ModerationRepository repository, ModeratedContent content,
                                              ReporterPseudonyms pseudonyms, ModeratedAccounts accounts,
                                              ReporterStandings standings, ActionQuota quota,
                                              UnitOfWork unitOfWork, Clock clock) {
        return new ReportContentUseCase(repository, content, pseudonyms, accounts, standings, quota, unitOfWork, clock);
    }

    @Bean
    ContentHoldUseCase contentHoldUseCase(ModerationRepository repository, UnitOfWork unitOfWork, Clock clock) {
        return new ContentHoldUseCase(repository, unitOfWork, clock);
    }

    @Bean
    ModerateContentUseCase moderateContentUseCase(ModerationRepository repository, ModeratedContent content,
                                                  ModeratedAccounts accounts, UnitOfWork unitOfWork, Clock clock) {
        return new ModerateContentUseCase(repository, content, accounts, unitOfWork, clock);
    }

    @Bean
    ModerationQueueUseCase moderationQueueUseCase(ModerationQueries queries, ModeratedContent content,
                                                  ModeratedAccounts accounts, Clock clock) {
        return new ModerationQueueUseCase(queries, content, accounts, clock);
    }

    @Bean
    ManageTeamUseCase manageTeamUseCase(ModeratedAccounts accounts, ModerationRepository repository,
                                        UnitOfWork unitOfWork, Clock clock) {
        return new ManageTeamUseCase(accounts, repository, unitOfWork, clock);
    }

    @Bean
    AdminMetricsUseCase adminMetricsUseCase(PlatformMetrics metrics, ModerationQueries queries, Clock clock) {
        return new AdminMetricsUseCase(metrics, queries, clock);
    }

    @Bean
    ModerationJanitor moderationJanitor(ModerationRepository repository, Clock clock) {
        return new ModerationJanitor(repository, clock);
    }

    /**
     * Minimização: o log de moderação some depois de {@link ModerationLogEntry#RETENTION_YEARS} anos, e denúncias
     * de conteúdo que não existe mais (apagado pelo autor, conta excluída) são descartadas.
     */
    static class ModerationJanitor {

        private final ModerationRepository repository;
        private final Clock clock;

        ModerationJanitor(ModerationRepository repository, Clock clock) {
            this.repository = repository;
            this.clock = clock;
        }

        @Scheduled(fixedDelayString = "PT6H", initialDelayString = "PT20M")
        @Transactional
        void purge() {
            repository.deleteOrphans();
            repository.deleteLogBefore(ZonedDateTime.now(clock).minusYears(ModerationLogEntry.RETENTION_YEARS).toInstant());
        }
    }
}
