package br.com.weediario.catalog.domain.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Chave de identidade dos itens do catálogo. Variações de digitação viram a mesma chave:
 * "Gorilla Glue #4", "gorilla-glue 4" e "GORILLA  GLUE 4" → "gorilla glue 4"; "Mother's Milk" → "mothers milk".
 * <p>
 * Para breeders também sai o sufixo genérico de loja: "Barneys Farm Seeds" e "Barney's Farm" → "barneys farm".
 * A migration V12 replica exatamente estas regras em SQL para os dados antigos.
 */
public final class CatalogKey {

    private static final Pattern APOSTROPHES = Pattern.compile("['’`]");
    private static final Pattern MARKS = Pattern.compile("\\p{M}");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern BREEDER_SUFFIX = Pattern.compile("( seeds? bank| seedbank| seed company| seed co| seeds?)$");

    private CatalogKey() {
    }

    public static String of(String raw) {
        if (raw == null) {
            return "";
        }
        String withoutAccents = MARKS.matcher(Normalizer.normalize(raw, Normalizer.Form.NFD)).replaceAll("");
        String withoutApostrophes = APOSTROPHES.matcher(withoutAccents.toLowerCase(Locale.ROOT)).replaceAll("");
        return NON_ALNUM.matcher(withoutApostrophes).replaceAll(" ").strip();
    }

    public static String breeder(String raw) {
        return BREEDER_SUFFIX.matcher(of(raw)).replaceAll("").strip();
    }
}
