package br.com.weediario.media.infrastructure.persistence;

import br.com.weediario.media.domain.model.Image;
import br.com.weediario.media.domain.model.ImageId;
import br.com.weediario.media.domain.model.ImageStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Sem nome original do arquivo, hash do conteúdo original, data de captura ou qualquer dado EXIF.
 */
@Entity
@Table(name = "images")
public class ImageJpaEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, updatable = false)
    private int width;

    @Column(nullable = false, updatable = false)
    private int height;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImageStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ImageJpaEntity() {
    }

    static ImageJpaEntity fromDomain(Image image) {
        ImageJpaEntity entity = new ImageJpaEntity();
        entity.id = image.id().value();
        entity.ownerId = image.ownerId();
        entity.width = image.width();
        entity.height = image.height();
        entity.status = image.status();
        entity.createdAt = image.createdAt();
        return entity;
    }

    Image toDomain() {
        return Image.restore(new ImageId(id), ownerId, width, height, status, createdAt);
    }
}
