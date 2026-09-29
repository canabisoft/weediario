package br.com.weediario.diary.application.query;

import br.com.weediario.diary.domain.model.DiaryStatus;
import br.com.weediario.diary.domain.model.FloweringType;
import br.com.weediario.diary.domain.model.GrowMedium;
import br.com.weediario.diary.domain.model.GrowthStage;
import br.com.weediario.diary.domain.model.SeedSex;
import br.com.weediario.diary.domain.model.SetupSnapshot;
import br.com.weediario.diary.domain.model.Visibility;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Lado de leitura (CQRS leve): consultas otimizadas para listagem, sem passar pelos aggregates.
 */
public interface DiaryReadModel {

    PageResult<DiarySummary> publicFeed(FeedFilter filter, PageQuery page);

    PageResult<DiarySummary> ownedBy(UUID ownerId, PageQuery page);

    Optional<DiarySummary> findVisible(UUID diaryId, Optional<UUID> viewerId);

    record FeedFilter(
            GrowthStage stage,
            GrowMedium medium,
            DiaryStatus status,
            FloweringType floweringType,
            String strain,
            FeedSort sort) {

        public FeedFilter {
            strain = strain == null || strain.isBlank() ? null : strain.strip();
            sort = sort == null ? FeedSort.RECENTLY_UPDATED : sort;
        }
    }

    enum FeedSort {
        /** Diários com update mais recente primeiro: o feed "vivo" da comunidade. */
        RECENTLY_UPDATED,
        /** Diários criados mais recentemente primeiro. */
        NEWEST,
        /** Diários com mais reações primeiro. */
        MOST_REACTED
    }

    /** Dados crus de um diário para cards e cabeçalhos; a idade do cultivo é calculada pelo caso de uso. */
    record DiarySummary(
            UUID id,
            String title,
            String author,
            String strainName,
            String breeder,
            FloweringType floweringType,
            SeedSex seedSex,
            GrowMedium medium,
            DiaryStatus status,
            Visibility visibility,
            GrowthStage currentStage,
            LocalDate startedOn,
            LocalDate endedOn,
            LocalDate lastUpdateOn,
            long updatesCount,
            long commentsCount,
            long reactionsCount,
            UUID coverPhotoId,
            SetupSnapshot setup) {
    }
}
