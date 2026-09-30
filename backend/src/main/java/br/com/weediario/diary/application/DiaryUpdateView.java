package br.com.weediario.diary.application;

import br.com.weediario.diary.domain.model.DiaryUpdate;
import br.com.weediario.diary.domain.model.GrowAge;
import br.com.weediario.diary.domain.model.GrowDiary;
import br.com.weediario.diary.domain.model.GrowthStage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Update pronto para exibição, já com o dia/semana do cultivo em que foi registrado.
 * {@code photos} traz as URLs de miniatura e de alta resolução; {@code photoIds} fica por compatibilidade.
 */
public record DiaryUpdateView(
        UUID id,
        GrowthStage stage,
        LocalDate recordedOn,
        long day,
        long week,
        String note,
        BigDecimal temperatureCelsius,
        BigDecimal relativeHumidity,
        BigDecimal wateringPh,
        BigDecimal plantHeightCm,
        List<UUID> photoIds,
        List<DiaryPhotoView> photos) {

    public static DiaryUpdateView of(DiaryUpdate update, GrowDiary diary) {
        GrowAge age = diary.ageOn(update.recordedOn());
        return new DiaryUpdateView(
                update.id().value(),
                update.stage(),
                update.recordedOn(),
                age.day(),
                age.week(),
                update.note(),
                update.metrics().temperatureCelsius(),
                update.metrics().relativeHumidity(),
                update.metrics().wateringPh(),
                update.metrics().plantHeightCm(),
                update.photoIds(),
                update.photoIds().stream().map(photoId -> DiaryPhotoView.of(diary.id().value(), photoId)).toList());
    }
}
