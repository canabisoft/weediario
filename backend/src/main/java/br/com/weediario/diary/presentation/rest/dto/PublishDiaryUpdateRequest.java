package br.com.weediario.diary.presentation.rest.dto;

import br.com.weediario.diary.domain.model.DiaryUpdate;
import br.com.weediario.diary.domain.model.GrowthStage;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * As fotos são enviadas antes, uma a uma, em {@code POST /api/v1/media/images}; aqui entram só os ids.
 */
public record PublishDiaryUpdateRequest(
        @NotNull GrowthStage stage,
        LocalDate recordedOn,
        @Size(max = DiaryUpdate.NOTE_MAX_LENGTH) String note,
        @DecimalMin("-10") @DecimalMax("60") @Digits(integer = 2, fraction = 1) BigDecimal temperatureCelsius,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 1) BigDecimal relativeHumidity,
        @DecimalMin("0") @DecimalMax("14") @Digits(integer = 2, fraction = 2) BigDecimal wateringPh,
        @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 1) BigDecimal plantHeightCm,
        @Size(max = DiaryUpdate.MAX_PHOTOS) List<@NotNull UUID> photoIds) {
}
