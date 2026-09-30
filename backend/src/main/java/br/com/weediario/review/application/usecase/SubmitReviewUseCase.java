package br.com.weediario.review.application.usecase;

import br.com.weediario.review.application.port.ReviewedGrows;
import br.com.weediario.review.domain.model.Ratings;
import br.com.weediario.review.domain.model.Review;
import br.com.weediario.review.domain.model.ReviewedGrow;
import br.com.weediario.review.domain.model.SeedbankRatings;
import br.com.weediario.review.domain.repository.ReviewRepository;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.util.UUID;

/**
 * Publica a review de um cultivo concluído. A genética e o banco avaliados são os do próprio diário, já ligados
 * ao catálogo (o usuário não escolhe livremente o que avaliar).
 */
public final class SubmitReviewUseCase {

    private final ReviewedGrows grows;
    private final ReviewRepository reviews;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public SubmitReviewUseCase(ReviewedGrows grows, ReviewRepository reviews, UnitOfWork unitOfWork, Clock clock) {
        this.grows = grows;
        this.reviews = reviews;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public SubmittedReview execute(Command command) {
        Ratings ratings = new Ratings(command.vigor(), command.pestResistance(), command.easeOfGrowth(),
                command.yield(), command.terpeneProfile());
        SeedbankRatings seedbankRatings = command.seedbankRatings();

        return unitOfWork.execute(() -> {
            ReviewedGrow grow = grows.find(command.diaryId())
                    .filter(found -> found.ownerId().equals(command.authorId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Diário"));
            if (reviews.existsForDiary(grow.diaryId())) {
                throw new DomainException("Este cultivo já foi avaliado");
            }
            Review review = reviews.save(Review.submit(grow, command.authorId(), ratings, seedbankRatings,
                    command.report(), clock));
            return new SubmittedReview(review.id(), review.strainId().value(), review.ratings().average());
        });
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
