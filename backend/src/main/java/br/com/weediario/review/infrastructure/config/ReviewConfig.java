package br.com.weediario.review.infrastructure.config;

import br.com.weediario.review.application.port.ReviewedGrows;
import br.com.weediario.review.application.query.BrowseStrainsUseCase;
import br.com.weediario.review.application.query.StrainCatalog;
import br.com.weediario.review.application.usecase.SubmitReviewUseCase;
import br.com.weediario.review.domain.repository.ReviewRepository;
import br.com.weediario.review.domain.repository.StrainRepository;
import br.com.weediario.shared.application.UnitOfWork;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ReviewConfig {

    @Bean
    SubmitReviewUseCase submitReviewUseCase(ReviewedGrows grows, StrainRepository strains, ReviewRepository reviews,
                                            UnitOfWork unitOfWork, Clock clock) {
        return new SubmitReviewUseCase(grows, strains, reviews, unitOfWork, clock);
    }

    @Bean
    BrowseStrainsUseCase browseStrainsUseCase(StrainCatalog catalog) {
        return new BrowseStrainsUseCase(catalog);
    }
}
