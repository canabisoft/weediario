package br.com.weediario.diary.application.query;

import br.com.weediario.diary.domain.model.DiaryStatus;
import br.com.weediario.diary.domain.model.FloweringType;
import br.com.weediario.diary.domain.model.GrowMedium;
import br.com.weediario.diary.domain.model.GrowthStage;
import br.com.weediario.diary.domain.model.Harvest;
import br.com.weediario.diary.domain.model.SeedSex;
import br.com.weediario.diary.domain.model.SetupSnapshot;
import br.com.weediario.diary.domain.model.Visibility;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lado de leitura (CQRS leve): consultas otimizadas para listagem, sem passar pelos aggregates.
 */
public interface DiaryReadModel {

    /**
     * @param viewerId necessário para o filtro {@link FeedFilter#following()}
     */
    PageResult<DiarySummary> publicFeed(FeedFilter filter, Optional<UUID> viewerId, PageQuery page);

    PageResult<DiarySummary> ownedBy(UUID ownerId, PageQuery page);

    Optional<DiarySummary> findVisible(UUID diaryId, Optional<UUID> viewerId);

    /**
     * Setups que o grower usou em diários públicos (a cópia congelada de cada cultivo, sem repetir as iguais),
     * do uso mais recente para o mais antigo. Setups nunca usados em diário público não aparecem.
     */
    List<PublicSetup> publicSetupsOf(String username, int limit);

    /**
     * @param diaries       diários públicos feitos com este setup
     * @param lastStartedOn início do cultivo mais recente com ele
     */
    record PublicSetup(SetupSnapshot setup, long diaries, LocalDate lastStartedOn) {
    }

    record FeedFilter(
            GrowthStage stage,
            GrowMedium medium,
            DiaryStatus status,
            FloweringType floweringType,
            String strain,
            FeedSort sort,
            boolean following,
            String author,
            UUID strainId,
            UUID breederId) {

        public FeedFilter {
            author = author == null || author.isBlank() ? null : author.strip();
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
            UUID strainId,
            FloweringType floweringType,
            SeedSex seedSex,
            GrowMedium medium,
            DiaryStatus status,
            Visibility visibility,
            GrowthStage currentStage,
            LocalDate startedOn,
            LocalDate endedOn,
            LocalDate lastUpdateOn,
            Harvest harvest,
            long updatesCount,
            long commentsCount,
            long reactionsCount,
            UUID coverPhotoId,
            SetupSnapshot setup,
            boolean moderationHold) {
    }
}
