package br.com.weediario.diary.application;

import br.com.weediario.diary.domain.model.DiaryStatus;
import br.com.weediario.diary.domain.model.FloweringType;
import br.com.weediario.diary.domain.model.GrowAge;
import br.com.weediario.diary.domain.model.GrowDiary;
import br.com.weediario.diary.domain.model.GrowMedium;
import br.com.weediario.diary.domain.model.GrowthStage;
import br.com.weediario.diary.domain.model.SeedSex;
import br.com.weediario.diary.domain.model.Visibility;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Projeção de leitura do diário devolvida pelos casos de uso, para que o aggregate não vaze para a camada web.
 */
public record GrowDiaryView(
        UUID id,
        String title,
        String strain,
        String breeder,
        FloweringType floweringType,
        SeedSex seedSex,
        GrowMedium medium,
        DiaryStatus status,
        Visibility visibility,
        LocalDate startedOn,
        LocalDate endedOn,
        GrowthStage currentStage,
        LocalDate lastUpdateOn,
        long currentDay,
        long currentWeek) {

    public static GrowDiaryView of(GrowDiary diary, LocalDate today) {
        GrowAge age = diary.ageOn(today);
        return new GrowDiaryView(
                diary.id().value(),
                diary.title(),
                diary.strain().name(),
                diary.strain().breeder(),
                diary.floweringType(),
                diary.seedSex(),
                diary.medium(),
                diary.status(),
                diary.visibility(),
                diary.startedOn(),
                diary.endedOn(),
                diary.currentStage(),
                diary.lastUpdateOn(),
                age.day(),
                age.week());
    }
}
