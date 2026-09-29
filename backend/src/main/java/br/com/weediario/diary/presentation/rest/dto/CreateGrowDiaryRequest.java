package br.com.weediario.diary.presentation.rest.dto;

import br.com.weediario.diary.domain.model.FloweringType;
import br.com.weediario.diary.domain.model.GrowDiary;
import br.com.weediario.diary.domain.model.GrowMedium;
import br.com.weediario.diary.domain.model.SeedSex;
import br.com.weediario.diary.domain.model.Strain;
import br.com.weediario.diary.domain.model.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload explícito (sem ownerId, status ou datas técnicas): o autor vem do JWT e o resto é definido pelo domínio,
 * o que impede mass assignment. Com {@code setupId}, o meio de cultivo pode ser omitido (vem do setup).
 */
public record CreateGrowDiaryRequest(
        @NotBlank @Size(max = GrowDiary.TITLE_MAX_LENGTH) String title,
        @NotBlank @Size(max = Strain.NAME_MAX_LENGTH) String strainName,
        @Size(max = Strain.BREEDER_MAX_LENGTH) String breeder,
        @NotNull FloweringType floweringType,
        @NotNull SeedSex seedSex,
        GrowMedium medium,
        Visibility visibility,
        @PastOrPresent LocalDate startedOn,
        UUID setupId) {
}
