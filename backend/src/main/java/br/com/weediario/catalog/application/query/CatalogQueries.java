package br.com.weediario.catalog.application.query;

import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lado de leitura do catálogo: autocomplete, listagens, detalhes e rankings de uso.
 * Contagens de uso consideram só diários públicos de contas ativas (mesmo critério do ranking de growers).
 */
public interface CatalogQueries {

    /** Autocomplete: começo do nome primeiro, depois semelhança, desempatando pelo mais usado. */
    List<Suggestion> suggestStrains(String query, UUID breederId, int limit);

    List<Suggestion> suggestBreeders(String query, int limit);

    List<Suggestion> suggestBrands(String query, int limit);

    List<Suggestion> suggestProducts(String query, UUID brandId, String category, int limit);

    PageResult<StrainListItem> searchStrains(String query, PageQuery page);

    PageResult<BreederListItem> searchBreeders(String query, PageQuery page);

    PageResult<ProductListItem> searchProducts(String query, String category, PageQuery page);

    Optional<StrainDetail> strain(UUID id);

    Optional<BreederListItem> breeder(UUID id);

    /** Todas as strains do banco no catálogo, as mais cultivadas primeiro. */
    PageResult<BreederStrainCard> strainsOfBreeder(UUID breederId, PageQuery page);

    PageResult<RankingRow> ranking(RankingKind kind, String category, PageQuery page);

    /**
     * Fila da moderação: itens cadastrados pela comunidade que ainda não foram verificados, mais novos primeiro,
     * cada um com os itens parecidos que podem ser a mesma coisa.
     */
    PageResult<PendingItem> pending(RankingKind kind, PageQuery page);

    /** Tamanho da fila da moderação por tipo. */
    Map<RankingKind, Long> pendingCounts();

    enum RankingKind {
        STRAINS,
        BREEDERS,
        BRANDS,
        PRODUCTS
    }

    record Suggestion(UUID id, String name, UUID parentId, String parentName, String category, String status,
                      String source, long usage) {
    }

    record StrainListItem(UUID id, String name, String breeder, UUID breederId, String floweringType, String dominance,
                          String status, String source, long diaries, long reviewCount, BigDecimal overall) {
    }

    /**
     * Card de uma strain na página do banco. {@code coverThumbnailUrl}: miniatura da foto mais recente de um
     * diário público com a strain (nulo se nenhum tiver foto).
     */
    record BreederStrainCard(UUID id, String name, String floweringType, String dominance, String floweringSpeed,
                             String genetics, String status, String source, long diaries, long reviewCount,
                             BigDecimal overall, BigDecimal avgGramsPerPlant, String coverThumbnailUrl) {
    }

    record BreederListItem(UUID id, String name, String status, String source, long strains, long diaries,
                           long reviewCount, BigDecimal overall) {
    }

    /** {@code recipes}: receitas públicas de nutrição que usam o produto. */
    record ProductListItem(UUID id, String name, UUID brandId, String brand, String category, String status,
                           String source, long diaries, long recipes) {
    }

    record StrainDetail(UUID id, String name, UUID breederId, String breeder, String floweringType, String dominance,
                        String floweringSpeed, List<String> seedTypes, List<String> flavors, List<String> effects,
                        String genetics, String status, String source, long diaries, long reviewCount,
                        BigDecimal overall, BigDecimal avgGramsPerPlant) {
    }

    /**
     * @param diaries        diários públicos que usam o item
     * @param growers        growers diferentes que usam o item
     * @param avgGramsPerPlant média de g/planta das colheitas registradas com o item (nulo se não há)
     */
    record RankingRow(long position, UUID id, String name, String parentName, String category, long diaries,
                      long growers, BigDecimal avgGramsPerPlant) {
    }

    /**
     * @param usage   diários (strains, breeders, produtos) ou produtos (marcas) que já usam o item
     * @param similar itens parecidos, candidatos a duplicata
     */
    record PendingItem(UUID id, String name, UUID parentId, String parentName, String category, Instant createdAt,
                       long usage, List<Suggestion> similar) {
    }
}
