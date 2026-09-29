package br.com.weediario.diary.application.usecase;

import br.com.weediario.diary.application.DiaryAccess;
import br.com.weediario.diary.application.port.PhotoLibrary;
import br.com.weediario.diary.application.port.PhotoLibrary.PhotoSize;
import br.com.weediario.diary.domain.model.GrowDiary;
import br.com.weediario.diary.domain.model.Visibility;
import br.com.weediario.diary.domain.repository.DiaryUpdateRepository;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;

import java.util.Optional;
import java.util.UUID;

/**
 * Entrega uma foto somente se ela pertence a um update do diário e o diário é visível para quem pede.
 * Assim, conhecer o id de uma foto de um diário privado não basta para vê-la.
 */
public final class GetDiaryPhotoUseCase {

    private final DiaryAccess access;
    private final DiaryUpdateRepository updates;
    private final PhotoLibrary photos;
    private final UnitOfWork unitOfWork;

    public GetDiaryPhotoUseCase(DiaryAccess access, DiaryUpdateRepository updates, PhotoLibrary photos,
                                UnitOfWork unitOfWork) {
        this.access = access;
        this.updates = updates;
        this.photos = photos;
        this.unitOfWork = unitOfWork;
    }

    public Photo execute(UUID diaryId, UUID photoId, PhotoSize size, Optional<UUID> viewerId) {
        GrowDiary diary = unitOfWork.execute(() -> {
            GrowDiary found = access.visibleTo(diaryId, viewerId);
            if (!updates.diaryContainsPhoto(found.id(), photoId)) {
                throw new ResourceNotFoundException("Foto");
            }
            return found;
        });
        byte[] content = photos.open(photoId, size).orElseThrow(() -> new ResourceNotFoundException("Foto"));
        return new Photo(content, diary.visibility() == Visibility.PUBLIC);
    }

    /** {@code publiclyCacheable} permite cache em CDN/proxy apenas para diários públicos. */
    public record Photo(byte[] content, boolean publiclyCacheable) {
    }
}
