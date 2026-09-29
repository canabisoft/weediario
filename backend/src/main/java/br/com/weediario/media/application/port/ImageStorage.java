package br.com.weediario.media.application.port;

import br.com.weediario.media.domain.model.ImageId;
import br.com.weediario.media.domain.model.ImageVariant;

import java.util.Optional;

/**
 * Armazenamento dos bytes já sanitizados. Hoje em disco local; trocável por S3/MinIO sem afetar o domínio.
 */
public interface ImageStorage {

    void store(ImageId id, ImageVariant variant, byte[] content);

    Optional<byte[]> load(ImageId id, ImageVariant variant);

    void delete(ImageId id);
}
