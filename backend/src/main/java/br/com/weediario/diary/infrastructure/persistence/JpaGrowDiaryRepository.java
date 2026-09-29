package br.com.weediario.diary.infrastructure.persistence;

import br.com.weediario.diary.domain.model.DiaryStatus;
import br.com.weediario.diary.domain.model.GrowDiary;
import br.com.weediario.diary.domain.model.GrowDiaryId;
import br.com.weediario.diary.domain.model.GrowerId;
import br.com.weediario.diary.domain.repository.GrowDiaryRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaGrowDiaryRepository implements GrowDiaryRepository {

    private final SpringDataGrowDiaryRepository jpa;

    public JpaGrowDiaryRepository(SpringDataGrowDiaryRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public GrowDiary save(GrowDiary diary) {
        return jpa.save(GrowDiaryJpaEntity.fromDomain(diary)).toDomain();
    }

    @Override
    public Optional<GrowDiary> findById(GrowDiaryId id) {
        return jpa.findById(id.value()).map(GrowDiaryJpaEntity::toDomain);
    }

    @Override
    public long countInProgressByOwner(GrowerId ownerId) {
        return jpa.countByOwnerIdAndStatus(ownerId.value(), DiaryStatus.IN_PROGRESS);
    }
}
