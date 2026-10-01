package br.com.weediario.review.infrastructure.config;

import br.com.weediario.review.application.port.ReviewModeration;
import br.com.weediario.review.application.port.ReviewedGrows;
import br.com.weediario.review.application.query.BrowseSeedbanksUseCase;
import br.com.weediario.review.application.query.BrowseStrainsUseCase;
import br.com.weediario.review.application.query.SeedbankCatalog;
import br.com.weediario.review.application.query.StrainCatalog;
import br.com.weediario.review.application.usecase.SubmitReviewUseCase;
import br.com.weediario.review.domain.repository.ReviewRepository;
import br.com.weediario.shared.application.UnitOfWork;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ReviewConfig {

    @Bean
    SubmitReviewUseCase submitReviewUseCase(ReviewedGrows grows, ReviewRepository reviews, ReviewModeration moderation,
                                            UnitOfWork unitOfWork, Clock clock) {
        return new SubmitReviewUseCase(grows, reviews, moderation, unitOfWork, clock);
    }

    @Bean
    BrowseSeedbanksUseCase browseSeedbanksUseCase(SeedbankCatalog catalog) {
        return new BrowseSeedbanksUseCase(catalog);
    }

    @Bean
    BrowseStrainsUseCase browseStrainsUseCase(StrainCatalog catalog) {
        return new BrowseStrainsUseCase(catalog);
    }
}
