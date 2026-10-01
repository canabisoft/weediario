package br.com.weediario.review.application.usecase;

import br.com.weediario.review.application.port.ReviewModeration;
import br.com.weediario.shared.domain.CommerceFilter;
import br.com.weediario.shared.domain.CommerceFilter.Verdict;

import br.com.weediario.review.application.port.ReviewedGrows;
import br.com.weediario.review.application.port.StrainTesting;
import br.com.weediario.review.domain.model.ProductRating;
import br.com.weediario.review.domain.model.Ratings;
import br.com.weediario.review.domain.model.Review;
import br.com.weediario.review.domain.model.ReviewedGrow;
import br.com.weediario.review.domain.model.SeedbankRatings;
import br.com.weediario.review.domain.repository.ReviewRepository;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Publica a review de um cultivo concluído. A genética e o banco avaliados são os do próprio diário, já ligados
 * ao catálogo (o usuário não escolhe livremente o que avaliar).
 */
public final class SubmitReviewUseCase {

    private final ReviewedGrows grows;
    private final ReviewRepository reviews;
    private final ReviewModeration moderation;
    private final StrainTesting strainTesting;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public SubmitReviewUseCase(ReviewedGrows grows, ReviewRepository reviews, ReviewModeration moderation,
                               StrainTesting strainTesting, UnitOfWork unitOfWork, Clock clock) {
        this.grows = grows;
        this.reviews = reviews;
        this.moderation = moderation;
        this.strainTesting = strainTesting;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public SubmittedReview execute(Command command) {
        Ratings ratings = new Ratings(command.vigor(), command.pestResistance(), command.easeOfGrowth(),
                command.yield(), command.terpeneProfile());
        SeedbankRatings seedbankRatings = command.seedbankRatings();
        // O relato é público: passa pelo filtro anti-comércio.
        Verdict verdict = CommerceFilter.requireAllowedOrHeld(command.report());

        List<ProductRating> productRatings = command.productRatings() == null ? List.of() : command.productRatings();

        SubmittedReview result = unitOfWork.execute(() -> {
            ReviewedGrow grow = grows.find(command.diaryId())
                    .filter(found -> found.ownerId().equals(command.authorId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Diário"));
            if (reviews.existsForDiary(grow.diaryId())) {
                throw new DomainException("Este cultivo já foi avaliado");
            }
            Review submitted = Review.submit(grow, command.authorId(), ratings, seedbankRatings, productRatings,
                    command.report(), clock);
            if (verdict.held()) {
                submitted.holdForModeration();
            }
            Review review = reviews.save(submitted);
            if (verdict.held()) {
                moderation.heldByFilter(review.id(), verdict.reason());
            }
            return new SubmittedReview(review.id(), review.strainId().value(), review.ratings().average());
        });
        // Fora da transação da review: se a strain estava em teste, esta avaliação pode levá-la ao banco oficial.
        if (!verdict.held()) {
            strainTesting.reviewed(result.strainId());
        }
        return result;
    }

    public record Command(
            UUID diaryId,
            UUID authorId,
            int vigor,
            int pestResistance,
            int easeOfGrowth,
            int yield,
            int terpeneProfile,
            Integer seedbankGermination,
            Integer seedbankStability,
            Integer seedbankService,
            List<ProductRating> productRatings,
            String report) {

        /** Notas do banco são opcionais, mas vêm completas ou não vêm. */
        SeedbankRatings seedbankRatings() {
            if (seedbankGermination == null && seedbankStability == null && seedbankService == null) {
                return null;
            }
            if (seedbankGermination == null || seedbankStability == null || seedbankService == null) {
                throw new DomainException("Avalie germinação, estabilidade e atendimento do banco juntos");
            }
            return new SeedbankRatings(seedbankGermination, seedbankStability, seedbankService);
        }
    }

    public record SubmittedReview(UUID id, UUID strainId, java.math.BigDecimal average) {
    }
}
