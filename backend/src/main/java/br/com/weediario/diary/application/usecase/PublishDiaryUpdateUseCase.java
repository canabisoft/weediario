package br.com.weediario.diary.application.usecase;

import br.com.weediario.diary.application.DiaryAccess;
import br.com.weediario.diary.application.DiaryUpdateView;
import br.com.weediario.diary.application.port.PhotoLibrary;
import br.com.weediario.diary.domain.model.DiaryUpdate;
import br.com.weediario.diary.domain.model.GrowDiary;
import br.com.weediario.diary.domain.model.GrowMetrics;
import br.com.weediario.diary.domain.model.GrowerId;
import br.com.weediario.diary.domain.model.GrowthStage;
import br.com.weediario.diary.domain.repository.DiaryUpdateRepository;
import br.com.weediario.diary.domain.repository.GrowDiaryRepository;
import br.com.weediario.shared.application.UnitOfWork;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Publica uma entrada na linha do tempo: valida, vincula as fotos já sanitizadas e avança o estágio do diário,
 * tudo na mesma transação.
 */
public final class PublishDiaryUpdateUseCase {

    private final DiaryAccess access;
    private final GrowDiaryRepository diaries;
    private final DiaryUpdateRepository updates;
    private final PhotoLibrary photos;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public PublishDiaryUpdateUseCase(DiaryAccess access, GrowDiaryRepository diaries, DiaryUpdateRepository updates,
                                     PhotoLibrary photos, UnitOfWork unitOfWork, Clock clock) {
        this.access = access;
        this.diaries = diaries;
        this.updates = updates;
        this.photos = photos;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public DiaryUpdateView execute(Command command) {
        GrowerId author = new GrowerId(command.authorId());
        return unitOfWork.execute(() -> {
            GrowDiary diary = access.ownedBy(command.diaryId(), command.authorId());
            DiaryUpdate update = DiaryUpdate.publish(
                    diary,
                    author,
                    command.stage(),
                    command.recordedOn() == null ? LocalDate.now(clock) : command.recordedOn(),
                    command.note(),
                    new GrowMetrics(command.temperatureCelsius(), command.relativeHumidity(),
                            command.wateringPh(), command.plantHeightCm()),
                    command.photoIds(),
                    clock);
            photos.attach(author, update.photoIds());
            diaries.save(diary);
            return DiaryUpdateView.of(updates.save(update), diary);
        });
    }

    public record Command(
            UUID diaryId,
            UUID authorId,
            GrowthStage stage,
            LocalDate recordedOn,
            String note,
            BigDecimal temperatureCelsius,
            BigDecimal relativeHumidity,
            BigDecimal wateringPh,
            BigDecimal plantHeightCm,
            List<UUID> photoIds) {
    }
}
