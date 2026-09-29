package br.com.weediario.review.infrastructure.persistence;

import br.com.weediario.review.domain.model.Ratings;
import br.com.weediario.review.domain.model.Review;
import br.com.weediario.review.domain.model.StrainId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reviews")
public class ReviewJpaEntity {

    @Id
    private UUID id;

    @Column(name = "strain_id", nullable = false, updatable = false)
    private UUID strainId;

    @Column(name = "diary_id", nullable = false, updatable = false)
    private UUID diaryId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(nullable = false)
    private short vigor;

    @Column(name = "pest_resistance", nullable = false)
    private short pestResistance;

    @Column(name = "ease_of_growth", nullable = false)
    private short easeOfGrowth;

    @Column(name = "yield_rating", nullable = false)
    private short yieldRating;

    @Column(name = "terpene_profile", nullable = false)
    private short terpeneProfile;

    @Column(nullable = false, length = Review.REPORT_MAX_LENGTH)
    private String report;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReviewJpaEntity() {
    }

    static ReviewJpaEntity fromDomain(Review review) {
        ReviewJpaEntity entity = new ReviewJpaEntity();
        entity.id = review.id();
        entity.strainId = review.strainId().value();
        entity.diaryId = review.diaryId();
        entity.authorId = review.authorId();
        entity.vigor = (short) review.ratings().vigor();
        entity.pestResistance = (short) review.ratings().pestResistance();
        entity.easeOfGrowth = (short) review.ratings().easeOfGrowth();
        entity.yieldRating = (short) review.ratings().yield();
        entity.terpeneProfile = (short) review.ratings().terpeneProfile();
        entity.report = review.report();
        entity.createdAt = review.createdAt();
        return entity;
    }

    Review toDomain() {
        return Review.restore(id, new StrainId(strainId), diaryId, authorId,
                new Ratings(vigor, pestResistance, easeOfGrowth, yieldRating, terpeneProfile), report, createdAt);
    }
}
