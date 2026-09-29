package br.com.weediario.diary.application.usecase;

import br.com.weediario.diary.application.DiaryAccess;
import br.com.weediario.diary.application.DiaryUpdateView;
import br.com.weediario.diary.domain.model.GrowDiary;
import br.com.weediario.diary.domain.repository.DiaryUpdateRepository;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.application.UnitOfWork;

import java.util.Optional;
import java.util.UUID;

public final class ListDiaryUpdatesUseCase {

    private final DiaryAccess access;
    private final DiaryUpdateRepository updates;
    private final UnitOfWork unitOfWork;

    public ListDiaryUpdatesUseCase(DiaryAccess access, DiaryUpdateRepository updates, UnitOfWork unitOfWork) {
        this.access = access;
        this.updates = updates;
        this.unitOfWork = unitOfWork;
    }

    public PageResult<DiaryUpdateView> execute(UUID diaryId, Optional<UUID> viewerId, PageQuery page) {
        return unitOfWork.execute(() -> {
            GrowDiary diary = access.visibleTo(diaryId, viewerId);
            var items = updates.findByDiary(diary.id(), (int) page.offset(), page.size()).stream()
                    .map(update -> DiaryUpdateView.of(update, diary))
                    .toList();
            return PageResult.of(items, page, updates.countByDiary(diary.id()));
        });
    }
}
