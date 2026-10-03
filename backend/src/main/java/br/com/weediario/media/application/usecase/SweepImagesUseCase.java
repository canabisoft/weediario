package br.com.weediario.media.application.usecase;

import br.com.weediario.media.application.port.ImageStorage;
import br.com.weediario.media.domain.model.ImageId;
import br.com.weediario.media.domain.repository.ImageRepository;
import br.com.weediario.shared.application.UnitOfWork;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Varredura das fotos (minimização de dados): nenhuma foto fica no servidor sem estar em uso.
 * <ul>
 *   <li>Foto publicada que nada mais usa (update apagado, foto de perfil trocada) é apagada do banco e do disco.</li>
 *   <li>Arquivo em disco sem registro no banco (ex.: o servidor caiu entre apagar a conta e apagar os arquivos) é
 *   apagado.</li>
 * </ul>
 * Só mexe no que tem mais de {@link #GRACE} de idade, para nunca pegar um upload em andamento.
 */
public final class SweepImagesUseCase {

    public static final Duration GRACE = Duration.ofHours(24);
    private static final int BATCH_SIZE = 500;

    private final ImageRepository images;
    private final ImageStorage storage;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public SweepImagesUseCase(ImageRepository images, ImageStorage storage, UnitOfWork unitOfWork, Clock clock) {
        this.images = images;
        this.storage = storage;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    /** @return quantas fotos (registro ou arquivo) foram apagadas */
    public int execute() {
        Instant cutoff = clock.instant().minus(GRACE);
        List<ImageId> unused = unitOfWork.execute(() -> {
            List<ImageId> ids = images.findUnreferencedBefore(cutoff, BATCH_SIZE);
            images.deleteAll(ids);
            return ids;
        });
        unused.forEach(storage::delete);

        Set<ImageId> onDisk = new HashSet<>(storage.storedBefore(cutoff));
        onDisk.removeAll(unitOfWork.execute(() -> images.existing(onDisk)));
        onDisk.forEach(storage::delete);
        return unused.size() + onDisk.size();
    }
}
