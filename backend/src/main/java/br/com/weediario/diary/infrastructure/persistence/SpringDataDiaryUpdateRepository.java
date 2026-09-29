package br.com.weediario.diary.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataDiaryUpdateRepository extends JpaRepository<DiaryUpdateJpaEntity, UUID> {

    List<DiaryUpdateJpaEntity> findByDiaryIdOrderByRecordedOnDescCreatedAtDesc(UUID diaryId, Pageable pageable);

    long countByDiaryId(UUID diaryId);

    Optional<DiaryUpdateJpaEntity> findFirstByDiaryIdOrderByRecordedOnDescCreatedAtDesc(UUID diaryId);

    @Query("""
            select count(u) > 0 from DiaryUpdateJpaEntity u join u.photoIds photo
            where u.diaryId = :diaryId and photo = :photoId""")
    boolean diaryContainsPhoto(@Param("diaryId") UUID diaryId, @Param("photoId") UUID photoId);
}
