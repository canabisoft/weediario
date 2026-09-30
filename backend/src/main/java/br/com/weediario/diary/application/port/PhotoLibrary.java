package br.com.weediario.diary.application.port;

import br.com.weediario.diary.domain.model.GrowerId;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso do contexto de diários às fotos já sanitizadas do contexto de mídia.
 */
public interface PhotoLibrary {

    /** Vincula fotos enviadas pelo próprio autor. Falha se alguma não existir, for de outra pessoa ou já estiver em uso. */
    void attach(GrowerId author, Collection<UUID> photoIds);

    Optional<PhotoFile> open(UUID photoId, PhotoSize size);

    void discard(Collection<UUID> photoIds);

    enum PhotoSize {
        FULL,
        THUMBNAIL
    }

    /** {@code mediaType} vem do formato em que a foto foi gravada (WebP nas novas, JPEG nas antigas). */
    record PhotoFile(byte[] content, String mediaType) {
    }
}
