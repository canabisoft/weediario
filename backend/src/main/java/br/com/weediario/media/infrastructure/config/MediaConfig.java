package br.com.weediario.media.infrastructure.config;

import br.com.weediario.media.application.ImageLibrary;
import br.com.weediario.media.application.port.ImageSanitizer;
import br.com.weediario.media.application.port.ImageStorage;
import br.com.weediario.media.application.usecase.PurgeOrphanImagesUseCase;
import br.com.weediario.media.application.usecase.SweepImagesUseCase;
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

    @Bean
    SweepImagesUseCase sweepImagesUseCase(ImageRepository images, ImageStorage storage, UnitOfWork unitOfWork,
                                          Clock clock) {
        return new SweepImagesUseCase(images, storage, unitOfWork, clock);
    }

    @Bean
    ImageSweepJanitor imageSweepJanitor(SweepImagesUseCase sweep) {
        return new ImageSweepJanitor(sweep);
    }

    /** Diária: apaga fotos que nada usa e arquivos sem registro (ex.: sobra de uma conta apagada). */
    public static class ImageSweepJanitor {

        private final SweepImagesUseCase sweep;

        ImageSweepJanitor(SweepImagesUseCase sweep) {
            this.sweep = sweep;
        }

        @Scheduled(fixedDelayString = "PT24H", initialDelayString = "PT20M")
        public int sweep() {
            int removed = sweep.execute();
            if (removed > 0) {
                log.info("{} foto(s) sem uso removidas do servidor", removed);
            }
            return removed;
        }
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
