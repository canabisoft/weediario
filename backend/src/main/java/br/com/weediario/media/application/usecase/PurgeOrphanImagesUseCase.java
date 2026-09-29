package br.com.weediario.media.application.usecase;

import br.com.weediario.media.application.port.ImageStorage;
import br.com.weediario.media.domain.model.Image;
import br.com.weediario.media.domain.model.ImageId;
import br.com.weediario.media.domain.repository.ImageRepository;
import br.com.weediario.shared.application.UnitOfWork;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Apaga imagens enviadas e nunca publicadas. Minimização de dados: fotos abandonadas não ficam para sempre no servidor.
 */
public final class PurgeOrphanImagesUseCase {

    public static final Duration ORPHAN_AFTER = Duration.ofHours(24);
    private static final int BATCH_SIZE = 200;

    private final ImageRepository images;
    private final ImageStorage storage;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public PurgeOrphanImagesUseCase(ImageRepository images, ImageStorage storage, UnitOfWork unitOfWork, Clock clock) {
        this.images = images;
        this.storage = storage;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public int execute() {
        Instant cutoff = clock.instant().minus(ORPHAN_AFTER);
        List<ImageId> purged = unitOfWork.execute(() -> {
            List<ImageId> ids = images.findPendingCreatedBefore(cutoff, BATCH_SIZE).stream()
                    .filter(image -> image.isOrphanSince(cutoff))
                    .map(Image::id)
                    .toList();
            images.deleteAll(ids);
            return ids;
        });
        purged.forEach(storage::delete);
        return purged.size();
    }
}
