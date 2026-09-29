package br.com.weediario.diary.infrastructure.persistence;

import br.com.weediario.diary.domain.model.DiaryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataGrowDiaryRepository extends JpaRepository<GrowDiaryJpaEntity, UUID> {

    long countByOwnerIdAndStatus(UUID ownerId, DiaryStatus status);
}
