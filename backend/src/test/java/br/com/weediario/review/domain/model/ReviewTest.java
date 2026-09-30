package br.com.weediario.review.domain.model;

import br.com.weediario.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC);
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID STRAIN = UUID.randomUUID();
    private static final String REPORT = "Planta vigorosa, flora de 9 semanas, aroma cítrico marcante.";

    @Test
    void ratingsMustBeBetweenOneAndFive() {
        assertThatThrownBy(() -> new Ratings(0, 3, 3, 3, 3)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Ratings(3, 3, 3, 6, 3)).isInstanceOf(DomainException.class);
        assertThat(new Ratings(5, 4, 4, 3, 5).average()).isEqualByComparingTo("4.2");
    }

    @Test
    void requiresCompletedGrowOfTheAuthor() {
        Ratings ratings = new Ratings(4, 4, 4, 4, 4);

        assertThatThrownBy(() -> Review.submit(grow(false, null), OWNER, ratings, null, REPORT, CLOCK))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("concluir");
        assertThatThrownBy(() -> Review.submit(grow(true, null), UUID.randomUUID(), ratings, null, REPORT, CLOCK))
                .isInstanceOf(DomainException.class);
        assertThat(Review.submit(grow(true, null), OWNER, ratings, null, REPORT, CLOCK).strainId().value())
                .isEqualTo(STRAIN);
    }

    @Test
    void reportNeedsMinimumContent() {
        assertThatThrownBy(() -> Review.submit(grow(true, null), OWNER, new Ratings(4, 4, 4, 4, 4), null, "top", CLOCK))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void seedbankCanOnlyBeRatedWhenTheDiaryNamesOne() {
        SeedbankRatings seedbankRatings = new SeedbankRatings(5, 4, 3);

        assertThatThrownBy(() -> Review.submit(grow(true, null), OWNER, new Ratings(4, 4, 4, 4, 4), seedbankRatings,
                REPORT, CLOCK))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("banco de sementes");

        UUID breeder = UUID.randomUUID();
        Review review = Review.submit(grow(true, breeder), OWNER, new Ratings(4, 4, 4, 4, 4), seedbankRatings, REPORT,
                CLOCK);
        assertThat(review.seedbankId()).contains(breeder);
        assertThat(seedbankRatings.average()).isEqualByComparingTo("4.0");
        assertThatThrownBy(() -> new SeedbankRatings(0, 3, 3)).isInstanceOf(DomainException.class);
    }

    private static ReviewedGrow grow(boolean completed, UUID breederId) {
        return new ReviewedGrow(UUID.randomUUID(), OWNER, completed, STRAIN, breederId);
    }
}
