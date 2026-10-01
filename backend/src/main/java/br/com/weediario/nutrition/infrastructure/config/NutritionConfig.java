package br.com.weediario.nutrition.infrastructure.config;

import br.com.weediario.nutrition.application.port.RecipeModeration;
import br.com.weediario.nutrition.application.port.GrowDiaries;
import br.com.weediario.nutrition.application.port.ProductCatalog;
import br.com.weediario.nutrition.application.query.RecipeReadModel;
import br.com.weediario.nutrition.application.usecase.BrowseRecipesUseCase;
import br.com.weediario.nutrition.application.usecase.DiaryRecipesUseCase;
import br.com.weediario.nutrition.application.usecase.ManageRecipesUseCase;
import br.com.weediario.nutrition.application.usecase.RecipeFeedbackUseCase;
import br.com.weediario.nutrition.application.usecase.RecommendRecipesUseCase;
import br.com.weediario.nutrition.domain.repository.RecipeFeedbackRepository;
import br.com.weediario.nutrition.domain.repository.RecipeRepository;
import br.com.weediario.shared.application.UnitOfWork;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class NutritionConfig {

    @Bean
    ManageRecipesUseCase manageRecipesUseCase(RecipeRepository recipes, RecipeReadModel readModel,
                                              ProductCatalog catalog, RecipeModeration moderation,
                                              UnitOfWork unitOfWork, Clock clock) {
        return new ManageRecipesUseCase(recipes, readModel, catalog, moderation, unitOfWork, clock);
    }

    @Bean
    BrowseRecipesUseCase browseRecipesUseCase(RecipeReadModel readModel) {
        return new BrowseRecipesUseCase(readModel);
    }

    @Bean
    DiaryRecipesUseCase diaryRecipesUseCase(GrowDiaries diaries, RecipeRepository recipes, RecipeReadModel readModel,
                                            UnitOfWork unitOfWork) {
        return new DiaryRecipesUseCase(diaries, recipes, readModel, unitOfWork);
    }

    @Bean
    RecipeFeedbackUseCase recipeFeedbackUseCase(RecipeRepository recipes, RecipeFeedbackRepository feedback,
                                                RecipeReadModel readModel, RecipeModeration moderation,
                                                UnitOfWork unitOfWork, Clock clock) {
        return new RecipeFeedbackUseCase(recipes, feedback, readModel, moderation, unitOfWork, clock);
    }

    @Bean
    RecommendRecipesUseCase recommendRecipesUseCase(RecipeReadModel readModel, GrowDiaries diaries, UnitOfWork unitOfWork) {
        return new RecommendRecipesUseCase(readModel, diaries, unitOfWork);
    }
}
