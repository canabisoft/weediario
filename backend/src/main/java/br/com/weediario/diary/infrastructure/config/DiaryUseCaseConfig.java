package br.com.weediario.diary.infrastructure.config;

import br.com.weediario.diary.application.DiaryAccess;
import br.com.weediario.diary.application.port.CatalogLookup;
import br.com.weediario.diary.application.port.GrowerDirectory;
import br.com.weediario.diary.application.port.PhotoLibrary;
import br.com.weediario.diary.application.port.UpdateNotifier;
import br.com.weediario.diary.application.query.BrowseDiariesUseCase;
import br.com.weediario.diary.application.query.DiaryReadModel;
import br.com.weediario.diary.application.usecase.ChangeDiaryProductsUseCase;
import br.com.weediario.diary.application.usecase.ChangeDiaryVisibilityUseCase;
import br.com.weediario.diary.application.usecase.CreateGrowDiaryUseCase;
import br.com.weediario.diary.application.usecase.DeleteDiaryUpdateUseCase;
import br.com.weediario.diary.application.usecase.FinishGrowDiaryUseCase;
import br.com.weediario.diary.application.usecase.GetDiaryPhotoUseCase;
import br.com.weediario.diary.application.usecase.ListDiaryUpdatesUseCase;
import br.com.weediario.diary.application.usecase.ManageGrowSetupsUseCase;
import br.com.weediario.diary.application.usecase.PublishDiaryUpdateUseCase;
import br.com.weediario.diary.domain.repository.DiaryUpdateRepository;
import br.com.weediario.diary.domain.repository.GrowDiaryRepository;
import br.com.weediario.diary.domain.repository.GrowSetupRepository;
import br.com.weediario.shared.application.UnitOfWork;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class DiaryUseCaseConfig {

    @Bean
    DiaryAccess diaryAccess(GrowDiaryRepository diaries) {
        return new DiaryAccess(diaries);
    }

    @Bean
    BrowseDiariesUseCase browseDiariesUseCase(DiaryReadModel readModel, Clock clock) {
        return new BrowseDiariesUseCase(readModel, clock);
    }

    @Bean
    CreateGrowDiaryUseCase createGrowDiaryUseCase(GrowDiaryRepository diaries, GrowSetupRepository setups,
                                                  CatalogLookup catalog, GrowerDirectory growers,
                                                  UnitOfWork unitOfWork, Clock clock) {
        return new CreateGrowDiaryUseCase(diaries, setups, catalog, growers, unitOfWork, clock);
    }

    @Bean
    ManageGrowSetupsUseCase manageGrowSetupsUseCase(GrowSetupRepository setups, CatalogLookup catalog,
                                                    UnitOfWork unitOfWork, Clock clock) {
        return new ManageGrowSetupsUseCase(setups, catalog, unitOfWork, clock);
    }

    @Bean
    FinishGrowDiaryUseCase finishGrowDiaryUseCase(DiaryAccess access, GrowDiaryRepository diaries,
                                                  UnitOfWork unitOfWork, Clock clock) {
        return new FinishGrowDiaryUseCase(access, diaries, unitOfWork, clock);
    }

    @Bean
    ChangeDiaryVisibilityUseCase changeDiaryVisibilityUseCase(DiaryAccess access, GrowDiaryRepository diaries,
                                                              UnitOfWork unitOfWork, Clock clock) {
        return new ChangeDiaryVisibilityUseCase(access, diaries, unitOfWork, clock);
    }

    @Bean
    PublishDiaryUpdateUseCase publishDiaryUpdateUseCase(DiaryAccess access, GrowDiaryRepository diaries,
                                                        DiaryUpdateRepository updates, PhotoLibrary photos,
                                                        UpdateNotifier notifier, UnitOfWork unitOfWork, Clock clock) {
        return new PublishDiaryUpdateUseCase(access, diaries, updates, photos, notifier, unitOfWork, clock);
    }

    @Bean
    DeleteDiaryUpdateUseCase deleteDiaryUpdateUseCase(DiaryAccess access, GrowDiaryRepository diaries,
                                                      DiaryUpdateRepository updates, PhotoLibrary photos,
                                                      UnitOfWork unitOfWork, Clock clock) {
        return new DeleteDiaryUpdateUseCase(access, diaries, updates, photos, unitOfWork, clock);
    }

    @Bean
    ListDiaryUpdatesUseCase listDiaryUpdatesUseCase(DiaryAccess access, DiaryUpdateRepository updates,
                                                    UnitOfWork unitOfWork) {
        return new ListDiaryUpdatesUseCase(access, updates, unitOfWork);
    }

    @Bean
    GetDiaryPhotoUseCase getDiaryPhotoUseCase(DiaryAccess access, DiaryUpdateRepository updates, PhotoLibrary photos,
                                              UnitOfWork unitOfWork) {
        return new GetDiaryPhotoUseCase(access, updates, photos, unitOfWork);
    }

    @Bean
    ChangeDiaryProductsUseCase changeDiaryProductsUseCase(DiaryAccess access, GrowDiaryRepository diaries,
                                                          CatalogLookup catalog, UnitOfWork unitOfWork, Clock clock) {
        return new ChangeDiaryProductsUseCase(access, diaries, catalog, unitOfWork, clock);
    }
}
