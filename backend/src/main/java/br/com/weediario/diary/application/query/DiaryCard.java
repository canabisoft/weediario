package br.com.weediario.diary.application.query;

import br.com.weediario.diary.application.query.DiaryReadModel.DiarySummary;
import br.com.weediario.diary.domain.model.DiaryStatus;
import br.com.weediario.diary.domain.model.FloweringType;
import br.com.weediario.diary.domain.model.GrowAge;
import br.com.weediario.diary.domain.model.GrowMedium;
import br.com.weediario.diary.domain.model.GrowthStage;
import br.com.weediario.diary.domain.model.SeedSex;
import br.com.weediario.diary.domain.model.Visibility;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Card do feed / cabeçalho do diário. O autor aparece só pelo apelido público.
 */
public record DiaryCard(
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
        long currentDay,
        long currentWeek,
        long updatesCount,
        UUID coverPhotoId) {

    public static DiaryCard of(DiarySummary summary, LocalDate today) {
        GrowAge age = GrowAge.of(summary.startedOn(), summary.endedOn(), today);
        return new DiaryCard(summary.id(), summary.title(), summary.author(), summary.strainName(), summary.breeder(),
                summary.floweringType(), summary.seedSex(), summary.medium(), summary.status(), summary.visibility(),
                summary.currentStage(), summary.startedOn(), summary.endedOn(), summary.lastUpdateOn(),
                age.day(), age.week(), summary.updatesCount(), summary.coverPhotoId());
    }
}
