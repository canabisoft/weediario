package br.com.weediario.diary.application.port;

import br.com.weediario.diary.domain.model.ProductSnapshot;

import java.util.List;
import java.util.UUID;

/**
 * Consulta ao contexto de catálogo (anti-corruption layer): strains e produtos usados nos diários e setups.
 */
public interface CatalogLookup {

    /** Strain escolhida no autocomplete. */
    CatalogStrainRef strain(UUID strainId);

    /** Strain digitada sem escolher do autocomplete: acha pelo nome (sem duplicar) ou cadastra. */
    CatalogStrainRef ensureStrain(String name, String breeder, UUID userId);

    /** Descreve produtos na ordem pedida; falha se algum não existir. */
    List<ProductSnapshot> products(List<UUID> productIds);

    record CatalogStrainRef(UUID strainId, String name, UUID breederId, String breederName) {
    }
}
