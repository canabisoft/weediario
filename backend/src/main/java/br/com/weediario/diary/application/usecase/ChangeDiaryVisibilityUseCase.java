package br.com.weediario.diary.application.usecase;

import br.com.weediario.diary.application.port.DiaryModeration;
import br.com.weediario.diary.domain.repository.DiaryUpdateRepository;
import br.com.weediario.shared.domain.CommerceFilter;
import br.com.weediario.shared.domain.CommerceFilter.Verdict;
import java.util.ArrayList;
import java.util.List;

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

    private static final int MAX_UPDATES_CHECKED = 1000;

    private final DiaryAccess access;
    private final GrowDiaryRepository diaries;
    private final DiaryUpdateRepository updates;
    private final DiaryModeration moderation;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ChangeDiaryVisibilityUseCase(DiaryAccess access, GrowDiaryRepository diaries, DiaryUpdateRepository updates,
                                        DiaryModeration moderation, UnitOfWork unitOfWork, Clock clock) {
        this.access = access;
        this.diaries = diaries;
        this.updates = updates;
        this.moderation = moderation;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public GrowDiaryView execute(UUID diaryId, UUID ownerId, Visibility visibility) {
        return unitOfWork.execute(() -> {
            GrowDiary diary = access.ownedBy(diaryId, ownerId);
            Verdict verdict = Verdict.ALLOWED;
            if (visibility == Visibility.PUBLIC && diary.visibility() != Visibility.PUBLIC && !diary.moderationHold()) {
                // Ao ficar público, o que foi escrito no privado passa pelo filtro anti-comércio.
                List<String> texts = new ArrayList<>();
                texts.add(diary.title());
                updates.findByDiary(diary.id(), 0, MAX_UPDATES_CHECKED).forEach(update -> texts.add(update.note()));
                verdict = CommerceFilter.requireAllowedOrHeld(texts.toArray(String[]::new));
            }
            if (verdict.held()) {
                diary.holdForModeration(clock);
            } else {
                diary.changeVisibility(visibility, clock);
            }
            GrowDiaryView view = GrowDiaryView.of(diaries.save(diary), LocalDate.now(clock));
            if (verdict.held()) {
                moderation.heldByFilter(diary.id().value(), verdict.reason());
            }
            return view;
        });
    }
}
