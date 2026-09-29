package br.com.weediario.diary.domain.model;

import br.com.weediario.shared.domain.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Métricas rápidas do ambiente, todas opcionais. Os limites barram erros de digitação óbvios
 * (ex.: 250 °C ou pH 70) sem impedir cenários reais.
 */
public record GrowMetrics(
        BigDecimal temperatureCelsius,
        BigDecimal relativeHumidity,
        BigDecimal wateringPh,
        BigDecimal plantHeightCm) {

    public static final GrowMetrics EMPTY = new GrowMetrics(null, null, null, null);

    public GrowMetrics {
        temperatureCelsius = within(temperatureCelsius, "-10", "60", 1, "Temperatura");
        relativeHumidity = within(relativeHumidity, "0", "100", 1, "Umidade");
        wateringPh = within(wateringPh, "0", "14", 2, "pH da rega");
        plantHeightCm = within(plantHeightCm, "0", "1000", 1, "Altura da planta");
    }

    public boolean isEmpty() {
        return temperatureCelsius == null && relativeHumidity == null && wateringPh == null && plantHeightCm == null;
    }

    private static BigDecimal within(BigDecimal value, String min, String max, int scale, String field) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(new BigDecimal(min)) < 0 || value.compareTo(new BigDecimal(max)) > 0) {
            throw new DomainException(field + " deve estar entre " + min + " e " + max);
        }
        return value.setScale(scale, RoundingMode.HALF_UP);
    }
}
