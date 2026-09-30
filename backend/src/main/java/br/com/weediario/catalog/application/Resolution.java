package br.com.weediario.catalog.application;

import java.util.List;

/**
 * Resultado de "achar ou cadastrar" um item do catálogo.
 *
 * @param outcome    EXISTING (já existia, pela chave ou apelido), SIMILAR (há nomes parecidos: o usuário confirma
 *                   qual é, ou pede para criar mesmo assim) ou CREATED (item novo)
 * @param item       o item resolvido (nulo quando SIMILAR)
 * @param candidates sugestões do "você quis dizer?" (só quando SIMILAR)
 */
public record Resolution(Outcome outcome, CatalogItem item, List<CatalogItem> candidates) {

    public enum Outcome {
        EXISTING,
        SIMILAR,
        CREATED
    }

    public static Resolution existing(CatalogItem item) {
        return new Resolution(Outcome.EXISTING, item, List.of());
    }

    public static Resolution similar(List<CatalogItem> candidates) {
        return new Resolution(Outcome.SIMILAR, null, List.copyOf(candidates));
    }

    public static Resolution created(CatalogItem item) {
        return new Resolution(Outcome.CREATED, item, List.of());
    }
}
