package br.com.weediario.media.domain.repository;

import br.com.weediario.media.domain.model.Image;
import br.com.weediario.media.domain.model.ImageId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ImageRepository {

    Image save(Image image);

    Optional<Image> findById(ImageId id);

    List<Image> findAllById(Collection<ImageId> ids);

    long countPendingByOwner(UUID ownerId);

    List<Image> findPendingCreatedBefore(Instant cutoff, int limit);

    void deleteAll(Collection<ImageId> ids);

    /** Quais destes ids ainda têm registro no banco. */
    Set<ImageId> existing(Collection<ImageId> ids);

    /** Imagens já publicadas que nada mais usa (update apagado, foto de perfil trocada...), criadas antes de cutoff. */
    List<ImageId> findUnreferencedBefore(Instant cutoff, int limit);
}
