package br.com.weediario.identity.infrastructure.media;

import br.com.weediario.identity.application.port.AvatarImages;
import br.com.weediario.media.application.ImageLibrary;
import br.com.weediario.media.domain.model.ImageVariant;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A foto de perfil usa a variante miniatura (até 400px de largura), suficiente para avatar e bem mais leve.
 */
@Component
public class MediaAvatarImages implements AvatarImages {

    private final ImageLibrary images;

    public MediaAvatarImages(ImageLibrary images) {
        this.images = images;
    }

    @Override
    public void attach(UUID ownerId, UUID imageId) {
        images.attach(ownerId, List.of(imageId));
    }

    @Override
    public void discard(UUID imageId) {
        images.discard(List.of(imageId));
    }

    @Override
    public Optional<AvatarFile> open(UUID imageId) {
        return images.open(imageId, ImageVariant.THUMBNAIL)
                .map(image -> new AvatarFile(image.content(), image.mediaType()));
    }
}
