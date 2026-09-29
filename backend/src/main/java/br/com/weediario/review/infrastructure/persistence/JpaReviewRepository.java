package br.com.weediario.review.infrastructure.persistence;

import br.com.weediario.review.domain.model.Review;
import br.com.weediario.review.domain.repository.ReviewRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class JpaReviewRepository implements ReviewRepository {

    private final SpringDataReviewRepository jpa;

    public JpaReviewRepository(SpringDataReviewRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Review save(Review review) {
        return jpa.save(ReviewJpaEntity.fromDomain(review)).toDomain();
    }

    @Override
    public boolean existsForDiary(UUID diaryId) {
        return jpa.existsByDiaryId(diaryId);
    }
}
