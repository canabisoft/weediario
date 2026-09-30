package br.com.weediario.notification.infrastructure.config;

import br.com.weediario.notification.application.port.NotificationPush;
import br.com.weediario.notification.application.query.NotificationReadModel;
import br.com.weediario.notification.application.usecase.MyNotificationsUseCase;
import br.com.weediario.notification.application.usecase.NotifyUseCase;
import br.com.weediario.notification.domain.repository.NotificationRepository;
import br.com.weediario.shared.application.UnitOfWork;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;

@Configuration
public class NotificationConfig {

    @Bean
    NotifyUseCase notifyUseCase(NotificationRepository notifications, NotificationPush push, UnitOfWork unitOfWork,
                                Clock clock) {
        return new NotifyUseCase(notifications, push, unitOfWork, clock);
    }

    @Bean
    MyNotificationsUseCase myNotificationsUseCase(NotificationReadModel readModel, NotificationRepository notifications,
                                                  NotificationPush push, UnitOfWork unitOfWork, Clock clock) {
        return new MyNotificationsUseCase(readModel, notifications, push, unitOfWork, clock);
    }

    @Bean
    ExpiredNotificationsJanitor expiredNotificationsJanitor(NotifyUseCase notify) {
        return new ExpiredNotificationsJanitor(notify);
    }

    /** Minimização de dados: notificações somem depois de {@link NotifyUseCase#RETENTION}. */
    static class ExpiredNotificationsJanitor {

        private final NotifyUseCase notify;

        ExpiredNotificationsJanitor(NotifyUseCase notify) {
            this.notify = notify;
        }

        @Scheduled(fixedDelayString = "PT6H", initialDelayString = "PT15M")
        void purge() {
            notify.purgeExpired();
        }
    }
}
