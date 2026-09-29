package br.com.weediario.media.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Imagem já sanitizada. Só guarda o que a plataforma produziu (dimensões após o redimensionamento);
 * nada da foto original (data de captura, câmera, GPS, nome do arquivo) é preservado.
 */
public final class Image {

    private final ImageId id;
    private final UUID ownerId;
    private final int width;
    private final int height;
    private ImageStatus status;
    private final Instant createdAt;

    private Image(ImageId id, UUID ownerId, int width, int height, ImageStatus status, Instant createdAt) {
        this.id = Guard.notNull(id, "Id");
        this.ownerId = Guard.notNull(ownerId, "Dono");
        if (width <= 0 || height <= 0) {
            throw new DomainException("Dimensões de imagem inválidas");
        }
        this.width = width;
        this.height = height;
        this.status = Guard.notNull(status, "Status");
        this.createdAt = Guard.notNull(createdAt, "Data de criação");
    }

    public static Image uploaded(UUID ownerId, int width, int height, Clock clock) {
        return new Image(ImageId.newId(), ownerId, width, height, ImageStatus.PENDING, clock.instant());
    }

    public static Image restore(ImageId id, UUID ownerId, int width, int height, ImageStatus status, Instant createdAt) {
        return new Image(id, ownerId, width, height, status, createdAt);
    }

    /**
     * Uma imagem só pode ser usada pelo próprio dono e uma única vez, o que impede que alguém
     * "sequestre" a foto de outro grower referenciando o id dela.
     */
    public void attach(UUID requesterId) {
        if (!ownerId.equals(requesterId)) {
            throw new DomainException("Imagem não encontrada");
        }
        if (status != ImageStatus.PENDING) {
            throw new DomainException("Imagem já utilizada em outro update");
        }
        status = ImageStatus.ATTACHED;
    }

    public boolean isOrphanSince(Instant cutoff) {
        return status == ImageStatus.PENDING && createdAt.isBefore(cutoff);
    }

    public ImageId id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public ImageStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
