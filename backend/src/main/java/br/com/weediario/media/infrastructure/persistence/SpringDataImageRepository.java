package br.com.weediario.media.infrastructure.persistence;

import br.com.weediario.media.domain.model.ImageStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface SpringDataImageRepository extends JpaRepository<ImageJpaEntity, UUID> {

    /** Lock para que a limpeza de órfãs não apague uma imagem que está sendo vinculada no mesmo instante. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ImageJpaEntity i where i.id in :ids")
    List<ImageJpaEntity> findAllByIdForUpdate(@Param("ids") Collection<UUID> ids);

    long countByOwnerIdAndStatus(UUID ownerId, ImageStatus status);

    /** {@code SKIP LOCKED}: imagens em processo de vínculo são ignoradas nesta rodada da limpeza. */
    @Query(value = """
            select * from images
            where status = 'PENDING' and created_at < :cutoff
            order by created_at
            limit :limit
            for update skip locked""", nativeQuery = true)
    List<ImageJpaEntity> findPendingCreatedBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    @Query(value = """
            select i.id from images i
             where i.status = 'ATTACHED' and i.created_at < :cutoff
               and not exists (select 1 from diary_update_photos p where p.image_id = i.id)
               and not exists (select 1 from users u where u.avatar_image_id = i.id)
             limit :limit""", nativeQuery = true)
    List<UUID> findUnreferencedBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
