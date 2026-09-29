package br.com.weediario.diary.application.usecase;

import br.com.weediario.diary.application.DiaryAccess;
import br.com.weediario.diary.application.GrowDiaryView;
import br.com.weediario.diary.domain.model.GrowDiary;
import br.com.weediario.diary.domain.model.Harvest;
import br.com.weediario.diary.domain.repository.GrowDiaryRepository;
import br.com.weediario.shared.application.UnitOfWork;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Conclui (colheita) ou cancela um cultivo. Só diários concluídos podem ser avaliados nas reviews.
 */
public final class FinishGrowDiaryUseCase {

    private final DiaryAccess access;
    private final GrowDiaryRepository diaries;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public FinishGrowDiaryUseCase(DiaryAccess access, GrowDiaryRepository diaries, UnitOfWork unitOfWork, Clock clock) {
        this.access = access;
        this.diaries = diaries;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public GrowDiaryView complete(UUID diaryId, UUID ownerId, LocalDate harvestedOn, Harvest harvest) {
        return unitOfWork.execute(() -> {
            GrowDiary diary = access.ownedBy(diaryId, ownerId);
            diary.complete(harvestedOn == null ? LocalDate.now(clock) : harvestedOn, harvest, clock);
            return GrowDiaryView.of(diaries.save(diary), LocalDate.now(clock));
        });
    }

    /** Peso seco informado depois da secagem/cura. */
    public GrowDiaryView recordHarvest(UUID diaryId, UUID ownerId, Harvest harvest) {
        return unitOfWork.execute(() -> {
            GrowDiary diary = access.ownedBy(diaryId, ownerId);
            diary.recordHarvest(harvest, clock);
            return GrowDiaryView.of(diaries.save(diary), LocalDate.now(clock));
        });
    }

    public GrowDiaryView cancel(UUID diaryId, UUID ownerId) {
        return unitOfWork.execute(() -> {
            GrowDiary diary = access.ownedBy(diaryId, ownerId);
            diary.cancel(clock);
            return GrowDiaryView.of(diaries.save(diary), LocalDate.now(clock));
        });
    }
}
