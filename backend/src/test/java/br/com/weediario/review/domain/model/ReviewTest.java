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
    private static final String REPORT = "Planta vigorosa, flora de 9 semanas, aroma cítrico marcante.";

    @Test
    void strainKeyIgnoresCaseAccentsAndPunctuation() {
        assertThat(StrainKey.of("Gorilla Glue #4", "GG Strains"))
                .isEqualTo(StrainKey.of("gorilla-glue  4", "gg strains"));
        assertThat(StrainKey.of("Açaí Kush", null)).isEqualTo(StrainKey.of("acai kush", ""));
    }

    @Test
    void sameStrainFromDifferentBreedersIsDifferent() {
        assertThat(StrainKey.of("Northern Lights", "Sensi Seeds"))
                .isNotEqualTo(StrainKey.of("Northern Lights", "Royal Queen"));
    }

    @Test
    void ratingsMustBeBetweenOneAndFive() {
        assertThatThrownBy(() -> new Ratings(0, 3, 3, 3, 3)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Ratings(3, 3, 3, 6, 3)).isInstanceOf(DomainException.class);
        assertThat(new Ratings(5, 4, 4, 3, 5).average()).isEqualByComparingTo("4.2");
    }

    @Test
    void requiresCompletedGrowOfTheAuthor() {
        Strain strain = Strain.register("Gorilla Glue #4", null, CLOCK);
        Ratings ratings = new Ratings(4, 4, 4, 4, 4);

        assertThatThrownBy(() -> Review.submit(grow(false), OWNER, strain, ratings, REPORT, CLOCK))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("concluir");
        assertThatThrownBy(() -> Review.submit(grow(true), UUID.randomUUID(), strain, ratings, REPORT, CLOCK))
                .isInstanceOf(DomainException.class);
        assertThat(Review.submit(grow(true), OWNER, strain, ratings, REPORT, CLOCK).strainId()).isEqualTo(strain.id());
    }

    @Test
    void reportNeedsMinimumContent() {
        Strain strain = Strain.register("Gorilla Glue #4", null, CLOCK);

        assertThatThrownBy(() -> Review.submit(grow(true), OWNER, strain, new Ratings(4, 4, 4, 4, 4), "top", CLOCK))
                .isInstanceOf(DomainException.class);
    }

    private static ReviewedGrow grow(boolean completed) {
        return new ReviewedGrow(UUID.randomUUID(), OWNER, completed, "Gorilla Glue #4", null);
    }
}
