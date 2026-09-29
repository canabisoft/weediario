package br.com.weediario.diary.application.usecase;

import br.com.weediario.diary.application.DiaryAccess;
import br.com.weediario.diary.application.port.PhotoLibrary;
import br.com.weediario.diary.domain.model.DiaryUpdate;
import br.com.weediario.diary.domain.model.DiaryUpdateId;
import br.com.weediario.diary.domain.model.GrowDiary;
import br.com.weediario.diary.domain.repository.DiaryUpdateRepository;
import br.com.weediario.diary.domain.repository.GrowDiaryRepository;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Remove um update e apaga de verdade as fotos dele (direito de exclusão), recalculando o estágio do diário.
 */
public final class DeleteDiaryUpdateUseCase {

    private final DiaryAccess access;
    private final GrowDiaryRepository diaries;
    private final DiaryUpdateRepository updates;
    private final PhotoLibrary photos;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public DeleteDiaryUpdateUseCase(DiaryAccess access, GrowDiaryRepository diaries, DiaryUpdateRepository updates,
                                    PhotoLibrary photos, UnitOfWork unitOfWork, Clock clock) {
        this.access = access;
        this.diaries = diaries;
        this.updates = updates;
        this.photos = photos;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public void execute(UUID diaryId, UUID updateId, UUID requesterId) {
        List<UUID> removedPhotos = unitOfWork.execute(() -> {
            GrowDiary diary = access.ownedBy(diaryId, requesterId);
            DiaryUpdate update = updates.findById(new DiaryUpdateId(updateId))
                    .filter(found -> found.belongsTo(diary.id()))
                    .orElseThrow(() -> new ResourceNotFoundException("Update"));
            updates.delete(update.id());
            diary.rebuildProgress(updates.findLatest(diary.id()), clock);
            diaries.save(diary);
            return update.photoIds();
        });
        // Arquivos só são apagados depois do commit: se a transação falhar, o update continua com as fotos.
        photos.discard(removedPhotos);
    }
}
