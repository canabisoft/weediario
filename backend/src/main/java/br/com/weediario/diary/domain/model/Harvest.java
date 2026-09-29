package br.com.weediario.diary.domain.model;

import br.com.weediario.shared.domain.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Resultado da colheita: peso seco total e número de plantas. É a base do "gramas por planta" do ranking.
 */
public record Harvest(int dryGrams, int plants) {

    public static final int MAX_DRY_GRAMS = 100_000;
    public static final int MAX_PLANTS = 500;

    public Harvest {
        if (dryGrams < 1 || dryGrams > MAX_DRY_GRAMS) {
            throw new DomainException("Peso seco deve estar entre 1 e " + MAX_DRY_GRAMS + " gramas");
        }
        if (plants < 1 || plants > MAX_PLANTS) {
            throw new DomainException("Número de plantas deve estar entre 1 e " + MAX_PLANTS);
        }
    }

    public BigDecimal gramsPerPlant() {
        return BigDecimal.valueOf(dryGrams).divide(BigDecimal.valueOf(plants), 1, RoundingMode.HALF_UP);
    }
}
