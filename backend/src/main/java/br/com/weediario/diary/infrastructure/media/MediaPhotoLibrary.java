package br.com.weediario.diary.infrastructure.media;

import br.com.weediario.diary.application.port.PhotoLibrary;
import br.com.weediario.diary.domain.model.GrowerId;
import br.com.weediario.media.application.ImageLibrary;
import br.com.weediario.media.domain.model.ImageVariant;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter entre "foto do diário" e "imagem" do contexto de mídia.
 */
@Component
public class MediaPhotoLibrary implements PhotoLibrary {

    private final ImageLibrary images;

    public MediaPhotoLibrary(ImageLibrary images) {
        this.images = images;
    }

    @Override
    public void attach(GrowerId author, Collection<UUID> photoIds) {
        if (!photoIds.isEmpty()) {
            images.attach(author.value(), photoIds);
        }
    }

    @Override
    public Optional<PhotoFile> open(UUID photoId, PhotoSize size) {
        return images.open(photoId, size == PhotoSize.FULL ? ImageVariant.FULL : ImageVariant.THUMBNAIL)
                .map(image -> new PhotoFile(image.content(), image.mediaType()));
    }

    @Override
    public void discard(Collection<UUID> photoIds) {
        if (!photoIds.isEmpty()) {
            images.discard(photoIds);
        }
    }
}
