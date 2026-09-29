package br.com.weediario.diary.domain.model;

import br.com.weediario.shared.domain.DomainException;

/**
 * Equipamentos e insumos de um setup. Tudo opcional: cada grower detalha o quanto quiser.
 * <p>
 * Os campos de texto são descrições livres ("LED Quantum Board 240W", "Exaustor 100mm"), sem HTML-encode
 * no backend: o Angular faz o escape na renderização.
 */
public record SetupSpec(
        Integer tentWidthCm,
        Integer tentDepthCm,
        Integer tentHeightCm,
        String lighting,
        Integer lightWattage,
        GrowMedium medium,
        String nutrients,
        String ventilation,
        String notes) {

    public static final int TEXT_MAX_LENGTH = 120;
    public static final int NOTES_MAX_LENGTH = 1000;
    public static final int DIMENSION_MIN_CM = 10;
    public static final int DIMENSION_MAX_CM = 1000;
    public static final int WATTAGE_MAX = 5000;

    public static final SetupSpec EMPTY = new SetupSpec(null, null, null, null, null, null, null, null, null);

    public SetupSpec {
        checkRange(tentWidthCm, DIMENSION_MIN_CM, DIMENSION_MAX_CM, "Largura da tenda (cm)");
        checkRange(tentDepthCm, DIMENSION_MIN_CM, DIMENSION_MAX_CM, "Profundidade da tenda (cm)");
        checkRange(tentHeightCm, DIMENSION_MIN_CM, DIMENSION_MAX_CM, "Altura da tenda (cm)");
        checkRange(lightWattage, 1, WATTAGE_MAX, "Potência da luz (W)");
        lighting = text(lighting, TEXT_MAX_LENGTH, "Iluminação");
        nutrients = text(nutrients, TEXT_MAX_LENGTH, "Nutrientes");
        ventilation = text(ventilation, TEXT_MAX_LENGTH, "Ventilação");
        notes = text(notes, NOTES_MAX_LENGTH, "Observações");
    }

    private static void checkRange(Integer value, int min, int max, String field) {
        if (value != null && (value < min || value > max)) {
            throw new DomainException(field + " deve estar entre " + min + " e " + max);
        }
    }

    private static String text(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "").strip();
        if (cleaned.length() > maxLength) {
            throw new DomainException(field + " deve ter no máximo " + maxLength + " caracteres");
        }
        return cleaned;
    }
}
