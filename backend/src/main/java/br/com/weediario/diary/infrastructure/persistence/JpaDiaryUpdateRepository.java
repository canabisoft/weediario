package br.com.weediario.diary.infrastructure.persistence;

import br.com.weediario.diary.domain.model.DiaryUpdate;
import br.com.weediario.diary.domain.model.DiaryUpdateId;
import br.com.weediario.diary.domain.model.GrowDiaryId;
import br.com.weediario.diary.domain.repository.DiaryUpdateRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaDiaryUpdateRepository implements DiaryUpdateRepository {

    private final SpringDataDiaryUpdateRepository jpa;

    public JpaDiaryUpdateRepository(SpringDataDiaryUpdateRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public DiaryUpdate save(DiaryUpdate update) {
        return jpa.save(DiaryUpdateJpaEntity.fromDomain(update)).toDomain();
    }

    @Override
    public Optional<DiaryUpdate> findById(DiaryUpdateId id) {
        return jpa.findById(id.value()).map(DiaryUpdateJpaEntity::toDomain);
    }

    @Override
    public List<DiaryUpdate> findByDiary(GrowDiaryId diaryId, int offset, int limit) {
        return jpa.findByDiaryIdOrderByRecordedOnDescCreatedAtDesc(diaryId.value(), PageRequest.of(offset / limit, limit))
                .stream()
                .map(DiaryUpdateJpaEntity::toDomain)
                .toList();
    }

    @Override
    public long countByDiary(GrowDiaryId diaryId) {
        return jpa.countByDiaryId(diaryId.value());
    }

    @Override
    public Optional<DiaryUpdate> findLatest(GrowDiaryId diaryId) {
        return jpa.findFirstByDiaryIdOrderByRecordedOnDescCreatedAtDesc(diaryId.value())
                .map(DiaryUpdateJpaEntity::toDomain);
    }

    @Override
    public boolean diaryContainsPhoto(GrowDiaryId diaryId, UUID photoId) {
        return jpa.diaryContainsPhoto(diaryId.value(), photoId);
    }

    @Override
    public void delete(DiaryUpdateId id) {
        jpa.deleteById(id.value());
        jpa.flush();
    }
}
