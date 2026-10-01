package br.com.weediario.nutrition.application.port;

import java.util.List;
import java.util.UUID;

/**
 * Consulta ao contexto de catálogo (anti-corruption layer): descreve produtos de marca usados nas receitas.
 */
public interface ProductCatalog {

    /** Na ordem pedida; falha (recurso não encontrado) se algum não existir. */
    List<CatalogProduct> describe(List<UUID> productIds);

    record CatalogProduct(UUID id, String name, String brand) {
    }
}
