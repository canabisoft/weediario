package br.com.weediario.diary.presentation.rest.dto;

import br.com.weediario.diary.domain.model.Harvest;
import br.com.weediario.shared.domain.DomainException;

import java.time.LocalDate;

/**
 * @param harvestedOn data da colheita; hoje, se omitida
 * @param dryGrams    peso seco total; opcional aqui, pode ser informado depois da cura
 * @param plants      número de plantas colhidas; obrigatório junto com o peso
 */
public record CompleteGrowDiaryRequest(LocalDate harvestedOn, Integer dryGrams, Integer plants) {

    public Harvest harvest() {
        if (dryGrams == null && plants == null) {
            return null;
        }
        if (dryGrams == null || plants == null) {
            throw new DomainException("Informe o peso seco e o número de plantas juntos");
        }
        return new Harvest(dryGrams, plants);
    }
}
