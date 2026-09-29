package br.com.weediario.diary.application.usecase;

import br.com.weediario.diary.application.DiaryAccess;
import br.com.weediario.diary.application.GrowDiaryView;
import br.com.weediario.diary.domain.model.GrowDiary;
import br.com.weediario.diary.domain.model.Visibility;
import br.com.weediario.diary.domain.repository.GrowDiaryRepository;
import br.com.weediario.shared.application.UnitOfWork;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tornar um diário privado o retira do feed e bloqueia o acesso às fotos dele imediatamente.
 */
public final class ChangeDiaryVisibilityUseCase {

    private final DiaryAccess access;
    private final GrowDiaryRepository diaries;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ChangeDiaryVisibilityUseCase(DiaryAccess access, GrowDiaryRepository diaries, UnitOfWork unitOfWork,
                                        Clock clock) {
        this.access = access;
        this.diaries = diaries;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public GrowDiaryView execute(UUID diaryId, UUID ownerId, Visibility visibility) {
        return unitOfWork.execute(() -> {
            GrowDiary diary = access.ownedBy(diaryId, ownerId);
            diary.changeVisibility(visibility, clock);
            return GrowDiaryView.of(diaries.save(diary), LocalDate.now(clock));
        });
    }
}
