package br.com.weediario.media.application.port;

import br.com.weediario.media.application.EncodedImage;
import br.com.weediario.media.domain.model.ImageId;
import br.com.weediario.media.domain.model.ImageVariant;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Armazenamento dos bytes já sanitizados. Hoje em disco local; trocável por S3/MinIO sem afetar o domínio.
 */
public interface ImageStorage {

    void store(ImageId id, ImageVariant variant, EncodedImage image);

    Optional<EncodedImage> load(ImageId id, ImageVariant variant);

    void delete(ImageId id);

    /** Ids de todas as imagens em disco cujos arquivos são mais antigos que {@code cutoff} (para a varredura). */
    Set<ImageId> storedBefore(Instant cutoff);
}
