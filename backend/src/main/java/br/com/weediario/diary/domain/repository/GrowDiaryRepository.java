package br.com.weediario.diary.domain.repository;

import br.com.weediario.diary.domain.model.GrowDiary;
import br.com.weediario.diary.domain.model.GrowDiaryId;
import br.com.weediario.diary.domain.model.GrowerId;

import java.util.Optional;

public interface GrowDiaryRepository {

    GrowDiary save(GrowDiary diary);

    Optional<GrowDiary> findById(GrowDiaryId id);

    long countInProgressByOwner(GrowerId ownerId);
}
