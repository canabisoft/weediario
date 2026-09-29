package br.com.weediario.media.infrastructure.persistence;

import br.com.weediario.media.domain.model.Image;
import br.com.weediario.media.domain.model.ImageId;
import br.com.weediario.media.domain.model.ImageStatus;
import br.com.weediario.media.domain.repository.ImageRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaImageRepository implements ImageRepository {

    private final SpringDataImageRepository jpa;

    public JpaImageRepository(SpringDataImageRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Image save(Image image) {
        return jpa.save(ImageJpaEntity.fromDomain(image)).toDomain();
    }

    @Override
    public Optional<Image> findById(ImageId id) {
        return jpa.findById(id.value()).map(ImageJpaEntity::toDomain);
    }

    @Override
    public List<Image> findAllById(Collection<ImageId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpa.findAllByIdForUpdate(ids.stream().map(ImageId::value).toList()).stream()
                .map(ImageJpaEntity::toDomain)
                .toList();
    }

    @Override
    public long countPendingByOwner(UUID ownerId) {
        return jpa.countByOwnerIdAndStatus(ownerId, ImageStatus.PENDING);
    }

    @Override
    public List<Image> findPendingCreatedBefore(Instant cutoff, int limit) {
        return jpa.findPendingCreatedBefore(cutoff, limit).stream().map(ImageJpaEntity::toDomain).toList();
    }

    @Override
    public void deleteAll(Collection<ImageId> ids) {
        if (!ids.isEmpty()) {
            jpa.deleteAllByIdInBatch(ids.stream().map(ImageId::value).toList());
        }
    }
}
