package br.com.weediario.media.infrastructure.storage;

import br.com.weediario.media.application.EncodedImage;
import br.com.weediario.media.application.port.ImageStorage;
import br.com.weediario.media.domain.model.ImageFormat;
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
import java.util.stream.Stream;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.time.Instant;

/**
 * Guarda os arquivos como {@code <raiz>/<2 primeiros chars do id>/<id>-<variante>.<webp|jpg>}.
 * O caminho é derivado apenas do UUID gerado pelo servidor, nunca do nome enviado pelo cliente (sem path traversal).
 * <p>
 * O formato fica na extensão: imagens antigas (JPEG) continuam legíveis sem migração, e a leitura não precisa
 * inspecionar o conteúdo para saber o Content-Type.
 */
@Component
public class FileSystemImageStorage implements ImageStorage {

    private final Path root;

    public FileSystemImageStorage(MediaProperties properties) throws IOException {
        this.root = Path.of(properties.storagePath()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public void store(ImageId id, ImageVariant variant, EncodedImage image) {
        Path target = pathOf(id, variant, image.format());
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(temp, image.content());
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar imagem", e);
        }
    }

    @Override
    public Optional<EncodedImage> load(ImageId id, ImageVariant variant) {
        for (ImageFormat format : ImageFormat.values()) {
            try {
                return Optional.of(new EncodedImage(Files.readAllBytes(pathOf(id, variant, format)), format));
            } catch (NoSuchFileException e) {
                // tenta o próximo formato
            } catch (IOException e) {
                throw new UncheckedIOException("Falha ao ler imagem", e);
            }
        }
        return Optional.empty();
    }

    @Override
    public void delete(ImageId id) {
        for (ImageVariant variant : ImageVariant.values()) {
            for (ImageFormat format : ImageFormat.values()) {
                try {
                    Files.deleteIfExists(pathOf(id, variant, format));
                } catch (IOException e) {
                    throw new UncheckedIOException("Falha ao apagar imagem", e);
                }
            }
        }
    }

    @Override
    public Set<ImageId> storedBefore(Instant cutoff) {
        Set<ImageId> ids = new HashSet<>();
        try (Stream<Path> files = Files.walk(root, 2)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                String name = file.getFileName().toString();
                if (name.length() < 36 || name.startsWith(".")) {
                    return;
                }
                try {
                    if (Files.getLastModifiedTime(file).toInstant().isBefore(cutoff)) {
                        ids.add(new ImageId(UUID.fromString(name.substring(0, 36))));
                    }
                } catch (IllegalArgumentException | IOException ignored) {
                    // Arquivo que não é imagem nossa (ou sumiu no meio da varredura): fica de fora.
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao listar imagens", e);
        }
        return ids;
    }

    private Path pathOf(ImageId id, ImageVariant variant, ImageFormat format) {
        String name = id.value().toString();
        Path path = root.resolve(name.substring(0, 2))
                .resolve(name + "-" + variant.name().toLowerCase(Locale.ROOT) + "." + format.extension())
                .normalize();
        if (!path.startsWith(root)) {
            throw new IllegalStateException("Caminho de imagem fora do diretório de armazenamento");
        }
        return path;
    }
}
