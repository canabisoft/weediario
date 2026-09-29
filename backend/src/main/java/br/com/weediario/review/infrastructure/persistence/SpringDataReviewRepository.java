package br.com.weediario.review.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataReviewRepository extends JpaRepository<ReviewJpaEntity, UUID> {

    boolean existsByDiaryId(UUID diaryId);
}
