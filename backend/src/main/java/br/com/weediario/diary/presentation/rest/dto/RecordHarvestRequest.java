package br.com.weediario.diary.presentation.rest.dto;

import br.com.weediario.diary.domain.model.Harvest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RecordHarvestRequest(
        @NotNull @Min(1) @Max(Harvest.MAX_DRY_GRAMS) Integer dryGrams,
        @NotNull @Min(1) @Max(Harvest.MAX_PLANTS) Integer plants) {

    public Harvest harvest() {
        return new Harvest(dryGrams, plants);
    }
}
