package br.com.weediario.review.domain.model;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Chave de identidade de uma genética no catálogo. Normaliza as variações comuns de digitação para que
 * "Gorilla Glue #4", "gorilla glue 4" e "Gorilla-Glue #4" sejam a mesma strain.
 * O breeder faz parte da chave: a mesma strain de bancos diferentes são genéticas diferentes.
 */
public record StrainKey(String name, String breeder) {

    public static StrainKey of(String name, String breeder) {
        return new StrainKey(normalize(name), breeder == null ? "" : normalize(breeder));
    }

    static String normalize(String raw) {
        String withoutAccents = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }
}
