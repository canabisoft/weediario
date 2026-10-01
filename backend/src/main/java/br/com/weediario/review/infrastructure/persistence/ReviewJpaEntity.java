package br.com.weediario.review.infrastructure.persistence;

import br.com.weediario.review.domain.model.ProductRating;
import br.com.weediario.review.domain.model.Ratings;
import br.com.weediario.review.domain.model.Review;
import br.com.weediario.review.domain.model.SeedbankRatings;
import br.com.weediario.review.domain.model.StrainId;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
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

    @Column(name = "seedbank_id")
    private UUID seedbankId;

    @Column(name = "seedbank_germination")
    private Short seedbankGermination;

    @Column(name = "seedbank_stability")
    private Short seedbankStability;

    @Column(name = "seedbank_service")
    private Short seedbankService;

    /** Notas dos produtos usados no cultivo (tabela {@code review_products}). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "review_products", joinColumns = @JoinColumn(name = "review_id"))
    private Set<ProductRatingEmbeddable> productRatings = new HashSet<>();

    @Column(nullable = false, length = Review.REPORT_MAX_LENGTH)
    private String report;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean hidden;

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
        entity.seedbankId = review.seedbankId().orElse(null);
        review.seedbankRatings().ifPresent(seedbank -> {
            entity.seedbankGermination = (short) seedbank.germination();
            entity.seedbankStability = (short) seedbank.stability();
            entity.seedbankService = (short) seedbank.service();
        });
        review.productRatings().forEach(product -> entity.productRatings.add(
                new ProductRatingEmbeddable(product.productId(), (short) product.rating())));
        entity.report = review.report();
        entity.createdAt = review.createdAt();
        entity.hidden = review.hidden();
        return entity;
    }

    Review toDomain() {
        return Review.restore(id, new StrainId(strainId), diaryId, authorId,
                new Ratings(vigor, pestResistance, easeOfGrowth, yieldRating, terpeneProfile), seedbankId,
                seedbankId == null ? null : new SeedbankRatings(seedbankGermination, seedbankStability, seedbankService),
                productRatings.stream()
                        .map(product -> new ProductRating(product.productId, product.rating))
                        .sorted(Comparator.comparing(ProductRating::productId))
                        .toList(),
                report, createdAt, hidden);
    }

    @Embeddable
    static class ProductRatingEmbeddable {

        @Column(name = "product_id", nullable = false)
        private UUID productId;

        @Column(nullable = false)
        private short rating;

        protected ProductRatingEmbeddable() {
        }

        ProductRatingEmbeddable(UUID productId, short rating) {
            this.productId = productId;
            this.rating = rating;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ProductRatingEmbeddable that && Objects.equals(productId, that.productId);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(productId);
        }
    }
}
