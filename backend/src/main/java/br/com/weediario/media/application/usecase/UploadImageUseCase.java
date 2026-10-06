package br.com.weediario.media.application.usecase;

import br.com.weediario.media.application.exception.InvalidImageException;
import br.com.weediario.media.application.port.ImageSanitizer;
import br.com.weediario.media.application.port.ImageSanitizer.SanitizedImage;
import br.com.weediario.media.application.port.ImageStorage;
import br.com.weediario.media.domain.model.Image;
import br.com.weediario.media.domain.model.ImageVariant;
import br.com.weediario.media.domain.repository.ImageRepository;
import br.com.weediario.shared.application.ActionLimits;
import br.com.weediario.shared.application.ActionQuota;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.util.UUID;

/**
 * Recebe a foto crua, sanitiza e só então grava. Os bytes originais nunca tocam o storage nem o banco.
 * <p>
 * Cada envio gasta a cota diária da conta antes do trabalho pesado (decodificar e recodificar), mesmo que a imagem
 * seja recusada. O teto de imagens sem uso é conferido de novo na gravação, com os envios da conta em fila: envios
 * em paralelo não passam dele.
 */
public final class UploadImageUseCase {

    public static final int MAX_BYTES = 12 * 1024 * 1024;
    public static final int MAX_PENDING_PER_OWNER = 30;

    private final ImageSanitizer sanitizer;
    private final ImageStorage storage;
    private final ImageRepository images;
    private final ActionQuota quota;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public UploadImageUseCase(ImageSanitizer sanitizer, ImageStorage storage, ImageRepository images,
                              ActionQuota quota, UnitOfWork unitOfWork, Clock clock) {
        this.sanitizer = sanitizer;
        this.storage = storage;
        this.images = images;
        this.quota = quota;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public UploadedImage execute(UUID ownerId, byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidImageException("Arquivo vazio");
        }
        if (content.length > MAX_BYTES) {
            throw new InvalidImageException("A imagem deve ter no máximo " + MAX_BYTES / (1024 * 1024) + " MB");
        }
        unitOfWork.run(() -> {
            ensureRoomForPending(ownerId);
            quota.consume(ownerId, ActionLimits.IMAGE_UPLOADS);
        });

        SanitizedImage sanitized = sanitizer.sanitize(content);
        Image image = Image.uploaded(ownerId, sanitized.width(), sanitized.height(), clock);

        storage.store(image.id(), ImageVariant.FULL, sanitized.full());
        storage.store(image.id(), ImageVariant.THUMBNAIL, sanitized.thumbnail());
        try {
            unitOfWork.run(() -> {
                images.lockUploadsOf(ownerId);
                ensureRoomForPending(ownerId);
                images.save(image);
            });
        } catch (RuntimeException e) {
            storage.delete(image.id());
            throw e;
        }
        return new UploadedImage(image.id().value(), image.width(), image.height());
    }

    private void ensureRoomForPending(UUID ownerId) {
        if (images.countPendingByOwner(ownerId) >= MAX_PENDING_PER_OWNER) {
            throw new DomainException("Muitas imagens enviadas sem uso. Publique um update antes de enviar mais.");
        }
    }

    public record UploadedImage(UUID id, int width, int height) {
    }
}
