package br.com.weediario.media.infrastructure.persistence;

import br.com.weediario.media.domain.model.Image;
import br.com.weediario.media.domain.model.ImageId;
import br.com.weediario.media.domain.model.ImageStatus;
import br.com.weediario.media.domain.repository.ImageRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaImageRepository implements ImageRepository {

    private final SpringDataImageRepository jpa;
    private final NamedParameterJdbcTemplate jdbc;

    public JpaImageRepository(SpringDataImageRepository jpa, NamedParameterJdbcTemplate jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
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

    /** Advisory lock da transação: some sozinho no commit ou rollback, sem tabela de travas. */
    @Override
    public void lockUploadsOf(UUID ownerId) {
        jdbc.query("select pg_advisory_xact_lock(hashtext('image-upload'), hashtext(cast(:owner as text)))",
                new MapSqlParameterSource("owner", ownerId), rs -> null);
    }

    @Override
    public List<Image> findPendingCreatedBefore(Instant cutoff, int limit) {
        return jpa.findPendingCreatedBefore(cutoff, limit).stream().map(ImageJpaEntity::toDomain).toList();
    }

    @Override
    public Set<ImageId> existing(Collection<ImageId> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return jpa.findAllById(ids.stream().map(ImageId::value).toList()).stream()
                .map(ImageJpaEntity::toDomain).map(Image::id).collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public List<ImageId> findUnreferencedBefore(Instant cutoff, int limit) {
        return jpa.findUnreferencedBefore(cutoff, limit).stream().map(ImageId::new).toList();
    }

    @Override
    public void deleteAll(Collection<ImageId> ids) {
        if (!ids.isEmpty()) {
            jpa.deleteAllByIdInBatch(ids.stream().map(ImageId::value).toList());
        }
    }
}
