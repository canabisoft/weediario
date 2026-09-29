package br.com.weediario.diary.domain.repository;

import br.com.weediario.diary.domain.model.DiaryUpdate;
import br.com.weediario.diary.domain.model.DiaryUpdateId;
import br.com.weediario.diary.domain.model.GrowDiaryId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiaryUpdateRepository {

    DiaryUpdate save(DiaryUpdate update);

    Optional<DiaryUpdate> findById(DiaryUpdateId id);

    /** Mais recentes primeiro (data do cultivo, depois ordem de publicação). */
    List<DiaryUpdate> findByDiary(GrowDiaryId diaryId, int offset, int limit);

    long countByDiary(GrowDiaryId diaryId);

    Optional<DiaryUpdate> findLatest(GrowDiaryId diaryId);

    boolean diaryContainsPhoto(GrowDiaryId diaryId, UUID photoId);

    void delete(DiaryUpdateId id);
}
