package br.com.weediario.diary.application;

import br.com.weediario.diary.domain.model.GrowDiary;
import br.com.weediario.diary.domain.model.GrowDiaryId;
import br.com.weediario.diary.domain.model.GrowerId;
import br.com.weediario.diary.domain.repository.GrowDiaryRepository;
import br.com.weediario.shared.application.ResourceNotFoundException;

import java.util.Optional;
import java.util.UUID;

/**
 * Carrega diários aplicando as regras de acesso. Diário privado de outra pessoa responde como inexistente (404),
 * sem revelar que ele existe.
 */
public final class DiaryAccess {

    private final GrowDiaryRepository diaries;

    public DiaryAccess(GrowDiaryRepository diaries) {
        this.diaries = diaries;
    }

    public GrowDiary visibleTo(UUID diaryId, Optional<UUID> viewerId) {
        return diaries.findById(new GrowDiaryId(diaryId))
                .filter(diary -> diary.isVisibleTo(viewerId.map(GrowerId::new)))
                .orElseThrow(DiaryAccess::notFound);
    }

    public GrowDiary ownedBy(UUID diaryId, UUID ownerId) {
        return diaries.findById(new GrowDiaryId(diaryId))
                .filter(diary -> diary.isOwnedBy(new GrowerId(ownerId)))
                .orElseThrow(DiaryAccess::notFound);
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Diário");
    }
}
