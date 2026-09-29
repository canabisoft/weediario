package br.com.weediario.social.infrastructure.config;

import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.social.application.port.DiaryAudience;
import br.com.weediario.social.application.port.Pseudonymizer;
import br.com.weediario.social.application.query.CommentReadModel;
import br.com.weediario.social.application.usecase.DiaryCommentsUseCase;
import br.com.weediario.social.application.usecase.DiaryReactionsUseCase;
import br.com.weediario.social.domain.repository.CommentRepository;
import br.com.weediario.social.domain.repository.ReactionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class SocialConfig {

    @Bean
    DiaryCommentsUseCase diaryCommentsUseCase(DiaryAudience diaries, CommentRepository comments,
                                              CommentReadModel readModel, Pseudonymizer pseudonymizer,
                                              UnitOfWork unitOfWork, Clock clock) {
        return new DiaryCommentsUseCase(diaries, comments, readModel, pseudonymizer, unitOfWork, clock);
    }

    @Bean
    DiaryReactionsUseCase diaryReactionsUseCase(DiaryAudience diaries, ReactionRepository reactions,
                                                Pseudonymizer pseudonymizer, UnitOfWork unitOfWork, Clock clock) {
        return new DiaryReactionsUseCase(diaries, reactions, pseudonymizer, unitOfWork, clock);
    }
}
