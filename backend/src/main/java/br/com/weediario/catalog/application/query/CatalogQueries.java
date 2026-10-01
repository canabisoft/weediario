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
 * Sementes em teste ({@code TESTING}) aparecem no autocomplete, no detalhe e na lista própria delas, mas ficam
 * fora da listagem oficial, dos rankings, da página do banco e do banco aberto.
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

    Optional<ProductListItem> product(UUID id);

    /**
     * Notas que o produto recebeu nas reviews pós-colheita (sem autor; o diário só vem se for público).
     */
    PageResult<ProductReview> productReviews(UUID productId, PageQuery page);

    /** Sementes em teste, com o andamento; opcionalmente de um breeder só. */
    PageResult<TestingStrainItem> testingStrains(String query, UUID breederId, PageQuery page);

    /** Pedidos de representação da própria pessoa (nunca exibidos para terceiros). */
    List<Representation> representationsOf(UUID userId);

    /** Sementes cadastradas pelos breeders que a pessoa representa (em teste ou já promovidas). */
    List<TestingStrainItem> breederStrainsOf(UUID userId);

    /** Fila da moderação: pedidos pendentes primeiro, depois os representantes aprovados (para revogar). */
    PageResult<RepresentativeRequest> representativeRequests(PageQuery page);

    long pendingRepresentatives();

    /**
     * Banco aberto: todas as strains oficiais (fora as em teste) com médias agregadas. Nenhum dado pessoal:
     * nada de apelidos, ids de diário ou de review.
     */
    List<OpenStrain> openStrains();

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

    /**
     * {@code recipes}: receitas públicas de nutrição que usam o produto. {@code rating}: média das notas dadas ao
     * produto nas reviews pós-colheita (nulo sem notas).
     */
    record ProductListItem(UUID id, String name, UUID brandId, String brand, String category, String status,
                           String source, long diaries, long recipes, long reviewCount, BigDecimal rating) {
    }

    /**
     * Nota de um produto em uma review. {@code diaryId} só vem se o diário for público.
     */
    record ProductReview(UUID id, UUID strainId, String strain, String breeder, int rating, UUID diaryId,
                         Instant createdAt) {
    }

    /**
     * @param promotedAt quando a semente em teste entrou no banco oficial
     * @param testing    andamento do teste (só para strains em teste)
     */
    record StrainDetail(UUID id, String name, UUID breederId, String breeder, String floweringType, String dominance,
                        String floweringSpeed, List<String> seedTypes, List<String> flavors, List<String> effects,
                        String genetics, String status, String source, long diaries, long reviewCount,
                        BigDecimal overall, BigDecimal avgGramsPerPlant, Instant promotedAt, Progress testing) {
    }

    /** Andamento do teste da comunidade (cultivadores e avaliações de fora do breeder). */
    record Progress(long testers, int testersRequired, long reviews, int reviewsRequired) {
    }

    record TestingStrainItem(UUID id, String name, UUID breederId, String breeder, String floweringType,
                             String dominance, String genetics, String status, Instant createdAt, Instant promotedAt,
                             Progress progress) {
    }

    record Representation(UUID breederId, String breeder, String status, Instant requestedAt, Instant decidedAt) {
    }

    /** Só para a moderação: inclui quem pediu e a nota de prova de vínculo. */
    record RepresentativeRequest(UUID breederId, String breeder, UUID userId, String username, String status,
                                 String note, Instant requestedAt, Instant decidedAt) {
    }

    /**
     * Linha do banco aberto. Médias só de reviews publicadas de contas ativas; diários e g/planta só de diários
     * públicos.
     */
    record OpenStrain(UUID id, String name, String breeder, String floweringType, String dominance,
                      String floweringSpeed, List<String> seedTypes, List<String> flavors, List<String> effects,
                      String genetics, String status, String source, Instant promotedAt, long publicDiaries,
                      long growers, long reviewCount, BigDecimal vigor, BigDecimal pestResistance,
                      BigDecimal easeOfGrowth, BigDecimal yield, BigDecimal terpeneProfile, BigDecimal overall,
                      BigDecimal avgGramsPerPlant) {
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
