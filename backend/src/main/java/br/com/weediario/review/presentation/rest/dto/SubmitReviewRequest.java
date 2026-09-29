package br.com.weediario.review.presentation.rest.dto;

import br.com.weediario.review.domain.model.Ratings;
import br.com.weediario.review.domain.model.Review;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Não há campo de strain nem de banco: avaliam-se sempre a genética e o breeder informados no diário.
 * As notas do banco de sementes são opcionais (as três juntas).
 */
public record SubmitReviewRequest(
        @NotNull @Min(Ratings.MIN) @Max(Ratings.MAX) Integer vigor,
        @NotNull @Min(Ratings.MIN) @Max(Ratings.MAX) Integer pestResistance,
        @NotNull @Min(Ratings.MIN) @Max(Ratings.MAX) Integer easeOfGrowth,
        @NotNull @Min(Ratings.MIN) @Max(Ratings.MAX) Integer yield,
        @NotNull @Min(Ratings.MIN) @Max(Ratings.MAX) Integer terpeneProfile,
        @Min(Ratings.MIN) @Max(Ratings.MAX) Integer seedbankGermination,
        @Min(Ratings.MIN) @Max(Ratings.MAX) Integer seedbankStability,
        @Min(Ratings.MIN) @Max(Ratings.MAX) Integer seedbankService,
        @NotBlank @Size(min = Review.REPORT_MIN_LENGTH, max = Review.REPORT_MAX_LENGTH) String report) {
}
