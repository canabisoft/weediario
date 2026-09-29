package br.com.weediario.media.application;

import br.com.weediario.media.application.port.ImageStorage;
import br.com.weediario.media.domain.model.Image;
import br.com.weediario.media.domain.model.ImageId;
import br.com.weediario.media.domain.model.ImageVariant;
import br.com.weediario.media.domain.repository.ImageRepository;
import br.com.weediario.shared.domain.DomainException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * API pública do contexto de mídia para os outros contextos. A autorização de <em>quem pode ver</em> a imagem
 * é responsabilidade de quem a usa (ex.: o diário decide pela visibilidade dele).
 * <p>
 * {@link #attach} deve rodar dentro da transação do chamador, para que o vínculo e o update sejam atômicos.
 */
public final class ImageLibrary {

    private final ImageRepository images;
    private final ImageStorage storage;

    public ImageLibrary(ImageRepository images, ImageStorage storage) {
        this.images = images;
        this.storage = storage;
    }

    public void attach(UUID requesterId, Collection<UUID> imageIds) {
        List<ImageId> ids = imageIds.stream().map(ImageId::new).toList();
        List<Image> found = images.findAllById(ids);
        if (found.size() != ids.size()) {
            throw new DomainException("Imagem não encontrada");
        }
        found.forEach(image -> {
            image.attach(requesterId);
            images.save(image);
        });
    }

    public Optional<byte[]> open(UUID imageId, ImageVariant variant) {
        return storage.load(new ImageId(imageId), variant);
    }

    /** Remove registro e arquivos. Os arquivos são apagados já, mesmo que a transação do chamador seja desfeita depois. */
    public void discard(Collection<UUID> imageIds) {
        List<ImageId> ids = imageIds.stream().map(ImageId::new).toList();
        images.deleteAll(ids);
        ids.forEach(storage::delete);
    }
}
