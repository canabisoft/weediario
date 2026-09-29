package br.com.weediario.media.infrastructure.config;

import br.com.weediario.media.application.ImageLibrary;
import br.com.weediario.media.application.port.ImageSanitizer;
import br.com.weediario.media.application.port.ImageStorage;
import br.com.weediario.media.application.usecase.PurgeOrphanImagesUseCase;
import br.com.weediario.media.application.usecase.UploadImageUseCase;
import br.com.weediario.media.domain.repository.ImageRepository;
import br.com.weediario.shared.application.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;

@Configuration
@EnableScheduling
public class MediaConfig {

    private static final Logger log = LoggerFactory.getLogger(MediaConfig.class);

    @Bean
    UploadImageUseCase uploadImageUseCase(ImageSanitizer sanitizer, ImageStorage storage, ImageRepository images,
                                          UnitOfWork unitOfWork, Clock clock) {
        return new UploadImageUseCase(sanitizer, storage, images, unitOfWork, clock);
    }

    @Bean
    ImageLibrary imageLibrary(ImageRepository images, ImageStorage storage) {
        return new ImageLibrary(images, storage);
    }

    @Bean
    PurgeOrphanImagesUseCase purgeOrphanImagesUseCase(ImageRepository images, ImageStorage storage,
                                                      UnitOfWork unitOfWork, Clock clock) {
        return new PurgeOrphanImagesUseCase(images, storage, unitOfWork, clock);
    }

    @Bean
    OrphanImageJanitor orphanImageJanitor(PurgeOrphanImagesUseCase purge) {
        return new OrphanImageJanitor(purge);
    }

    static class OrphanImageJanitor {

        private final PurgeOrphanImagesUseCase purge;

        OrphanImageJanitor(PurgeOrphanImagesUseCase purge) {
            this.purge = purge;
        }

        @Scheduled(fixedDelayString = "${weediario.media.orphan-purge-interval:PT1H}", initialDelayString = "PT5M")
        void purgeOrphans() {
            int purged = purge.execute();
            if (purged > 0) {
                log.info("{} imagens órfãs removidas", purged);
            }
        }
    }
}
