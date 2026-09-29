package br.com.weediario.review.application.usecase;

import br.com.weediario.review.application.port.ReviewedGrows;
import br.com.weediario.review.domain.model.Ratings;
import br.com.weediario.review.domain.model.Review;
import br.com.weediario.review.domain.model.ReviewedGrow;
import br.com.weediario.review.domain.model.Seedbank;
import br.com.weediario.review.domain.model.SeedbankRatings;
import br.com.weediario.review.domain.model.Strain;
import br.com.weediario.review.domain.model.StrainKey;
import br.com.weediario.review.domain.repository.ReviewRepository;
import br.com.weediario.review.domain.repository.SeedbankRepository;
import br.com.weediario.review.domain.repository.StrainRepository;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.util.UUID;

/**
 * Publica a review de um cultivo concluído. A genética avaliada é a do próprio diário (o usuário não escolhe
 * livremente qual strain avaliar); ela entra no catálogo na primeira review.
 */
public final class SubmitReviewUseCase {

    private final ReviewedGrows grows;
    private final StrainRepository strains;
    private final SeedbankRepository seedbanks;
    private final ReviewRepository reviews;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public SubmitReviewUseCase(ReviewedGrows grows, StrainRepository strains, SeedbankRepository seedbanks,
                               ReviewRepository reviews, UnitOfWork unitOfWork, Clock clock) {
        this.grows = grows;
        this.strains = strains;
        this.seedbanks = seedbanks;
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
            Strain strain = strains.findByKey(StrainKey.of(grow.strainName(), grow.breeder()))
                    .orElseGet(() -> strains.save(Strain.register(grow.strainName(), grow.breeder(), clock)));

            Seedbank seedbank = seedbankRatings == null || grow.breeder() == null ? null : seedbankOf(grow.breeder());

            Review review = reviews.save(Review.submit(grow, command.authorId(), strain, ratings, seedbank,
                    seedbankRatings, command.report(), clock));
            return new SubmittedReview(review.id(), strain.id().value(), review.ratings().average());
        });
    }

    private Seedbank seedbankOf(String breeder) {
        return seedbanks.findByKey(Seedbank.keyOf(breeder))
                .orElseGet(() -> seedbanks.save(Seedbank.register(breeder, clock)));
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
