package br.com.weediario.social.infrastructure.config;

import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.social.application.port.DiaryAudience;
import br.com.weediario.social.application.port.GrowerLookup;
import br.com.weediario.social.application.port.InteractionNotifier;
import br.com.weediario.social.application.port.Pseudonymizer;
import br.com.weediario.social.application.query.CommentReadModel;
import br.com.weediario.social.application.query.GrowerDirectory;
import br.com.weediario.social.application.usecase.DiaryCommentsUseCase;
import br.com.weediario.social.application.usecase.DiaryReactionsUseCase;
import br.com.weediario.social.application.usecase.GrowersUseCase;
import br.com.weediario.social.application.usecase.ModerateCommentsUseCase;
import br.com.weediario.social.domain.repository.CommentRepository;
import br.com.weediario.social.domain.repository.FollowRepository;
import br.com.weediario.social.domain.repository.ReactionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class SocialConfig {

    @Bean
    DiaryCommentsUseCase diaryCommentsUseCase(DiaryAudience diaries, CommentRepository comments,
                                              CommentReadModel readModel, Pseudonymizer pseudonymizer,
                                              InteractionNotifier notifier, UnitOfWork unitOfWork, Clock clock) {
        return new DiaryCommentsUseCase(diaries, comments, readModel, pseudonymizer, notifier, unitOfWork, clock);
    }

    @Bean
    GrowersUseCase growersUseCase(GrowerDirectory directory, GrowerLookup growers, FollowRepository follows,
                                  InteractionNotifier notifier, UnitOfWork unitOfWork, Clock clock) {
        return new GrowersUseCase(directory, growers, follows, notifier, unitOfWork, clock);
    }

    @Bean
    DiaryReactionsUseCase diaryReactionsUseCase(DiaryAudience diaries, ReactionRepository reactions,
                                                Pseudonymizer pseudonymizer, InteractionNotifier notifier,
                                                UnitOfWork unitOfWork, Clock clock) {
        return new DiaryReactionsUseCase(diaries, reactions, pseudonymizer, notifier, unitOfWork, clock);
    }

    @Bean
    ModerateCommentsUseCase moderateCommentsUseCase(CommentRepository comments, CommentReadModel readModel,
                                                    UnitOfWork unitOfWork) {
        return new ModerateCommentsUseCase(comments, readModel, unitOfWork);
    }
}
