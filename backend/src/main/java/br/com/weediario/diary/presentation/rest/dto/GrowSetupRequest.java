package br.com.weediario.diary.presentation.rest.dto;

import br.com.weediario.diary.domain.model.GrowEnvironment;
import br.com.weediario.diary.domain.model.GrowMedium;
import br.com.weediario.diary.domain.model.GrowSetup;
import br.com.weediario.diary.domain.model.SetupSpec;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record GrowSetupRequest(
        @NotBlank @Size(max = GrowSetup.NAME_MAX_LENGTH) String name,
        @NotNull GrowEnvironment environment,
        @Min(SetupSpec.DIMENSION_MIN_CM) @Max(SetupSpec.DIMENSION_MAX_CM) Integer tentWidthCm,
        @Min(SetupSpec.DIMENSION_MIN_CM) @Max(SetupSpec.DIMENSION_MAX_CM) Integer tentDepthCm,
        @Min(SetupSpec.DIMENSION_MIN_CM) @Max(SetupSpec.DIMENSION_MAX_CM) Integer tentHeightCm,
        @Size(max = SetupSpec.TEXT_MAX_LENGTH) String lighting,
        @Min(1) @Max(SetupSpec.WATTAGE_MAX) Integer lightWattage,
        GrowMedium medium,
        @Size(max = SetupSpec.TEXT_MAX_LENGTH) String nutrients,
        @Size(max = SetupSpec.TEXT_MAX_LENGTH) String ventilation,
        @Size(max = SetupSpec.NOTES_MAX_LENGTH) String notes,
        @Size(max = GrowSetup.MAX_PRODUCTS) List<@NotNull UUID> productIds) {

    public SetupSpec spec() {
        return new SetupSpec(tentWidthCm, tentDepthCm, tentHeightCm, lighting, lightWattage, medium,
                nutrients, ventilation, notes);
    }
}
