package br.com.weediario.media.infrastructure.storage;

import br.com.weediario.media.application.port.ImageStorage;
import br.com.weediario.media.domain.model.ImageId;
import br.com.weediario.media.domain.model.ImageVariant;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;

/**
 * Guarda os arquivos como {@code <raiz>/<2 primeiros chars do id>/<id>-<variante>.jpg}.
 * O caminho é derivado apenas do UUID gerado pelo servidor, nunca do nome enviado pelo cliente (sem path traversal).
 */
@Component
public class FileSystemImageStorage implements ImageStorage {

    private final Path root;

    public FileSystemImageStorage(MediaProperties properties) throws IOException {
        this.root = Path.of(properties.storagePath()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public void store(ImageId id, ImageVariant variant, byte[] content) {
        Path target = pathOf(id, variant);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar imagem", e);
        }
    }

    @Override
    public Optional<byte[]> load(ImageId id, ImageVariant variant) {
        try {
            return Optional.of(Files.readAllBytes(pathOf(id, variant)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler imagem", e);
        }
    }

    @Override
    public void delete(ImageId id) {
        for (ImageVariant variant : ImageVariant.values()) {
            try {
                Files.deleteIfExists(pathOf(id, variant));
            } catch (IOException e) {
                throw new UncheckedIOException("Falha ao apagar imagem", e);
            }
        }
    }

    private Path pathOf(ImageId id, ImageVariant variant) {
        String name = id.value().toString();
        Path path = root.resolve(name.substring(0, 2))
                .resolve(name + "-" + variant.name().toLowerCase(Locale.ROOT) + ".jpg")
                .normalize();
        if (!path.startsWith(root)) {
            throw new IllegalStateException("Caminho de imagem fora do diretório de armazenamento");
        }
        return path;
    }
}
