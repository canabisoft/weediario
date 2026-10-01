package br.com.weediario.catalog.application.query;

import br.com.weediario.catalog.application.query.CatalogQueries.BreederListItem;
import br.com.weediario.catalog.application.query.CatalogQueries.BreederStrainCard;
import br.com.weediario.catalog.application.query.CatalogQueries.OpenStrain;
import br.com.weediario.catalog.application.query.CatalogQueries.PendingItem;
import br.com.weediario.catalog.application.query.CatalogQueries.ProductListItem;
import br.com.weediario.catalog.application.query.CatalogQueries.ProductReview;
import br.com.weediario.catalog.application.query.CatalogQueries.RankingKind;
import br.com.weediario.catalog.application.query.CatalogQueries.RankingRow;
import br.com.weediario.catalog.application.query.CatalogQueries.Representation;
import br.com.weediario.catalog.application.query.CatalogQueries.RepresentativeRequest;
import br.com.weediario.catalog.application.query.CatalogQueries.StrainDetail;
import br.com.weediario.catalog.application.query.CatalogQueries.StrainListItem;
import br.com.weediario.catalog.application.query.CatalogQueries.Suggestion;
import br.com.weediario.catalog.application.query.CatalogQueries.TestingStrainItem;
import br.com.weediario.catalog.domain.model.CatalogKey;
import br.com.weediario.shared.application.ModeratorOnlyException;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.application.ResourceNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BrowseCatalogUseCase {

    public static final int SUGGESTIONS = 8;
    /** Autocomplete só a partir de 2 caracteres úteis, para não varrer o catálogo inteiro a cada tecla. */
    private static final int MIN_QUERY = 2;

    private final CatalogQueries queries;

    public BrowseCatalogUseCase(CatalogQueries queries) {
        this.queries = queries;
    }

    public List<Suggestion> suggestStrains(String query, UUID breederId) {
        return tooShort(query) ? List.of() : queries.suggestStrains(query, breederId, SUGGESTIONS);
    }

    public List<Suggestion> suggestBreeders(String query) {
        return tooShort(query) ? List.of() : queries.suggestBreeders(query, SUGGESTIONS);
    }

    public List<Suggestion> suggestBrands(String query) {
        return tooShort(query) ? List.of() : queries.suggestBrands(query, SUGGESTIONS);
    }

    public List<Suggestion> suggestProducts(String query, UUID brandId, String category) {
        return tooShort(query) ? List.of() : queries.suggestProducts(query, brandId, category, SUGGESTIONS);
    }

    public PageResult<StrainListItem> strains(String query, PageQuery page) {
        return queries.searchStrains(normalizedOrNull(query), page);
    }

    public PageResult<BreederListItem> breeders(String query, PageQuery page) {
        return queries.searchBreeders(normalizedOrNull(query), page);
    }

    public PageResult<ProductListItem> products(String query, String category, PageQuery page) {
        return queries.searchProducts(normalizedOrNull(query), category, page);
    }

    public StrainDetail strain(UUID id) {
        return queries.strain(id).orElseThrow(() -> new ResourceNotFoundException("Strain"));
    }

    public ProductListItem product(UUID id) {
        return queries.product(id).orElseThrow(() -> new ResourceNotFoundException("Produto"));
    }

    public PageResult<ProductReview> productReviews(UUID productId, PageQuery page) {
        product(productId);
        return queries.productReviews(productId, page);
    }

    /** Sementes em teste (lista pública, para quem quer ajudar a testar). */
    public PageResult<TestingStrainItem> testingStrains(String query, UUID breederId, PageQuery page) {
        return queries.testingStrains(normalizedOrNull(query), breederId, page);
    }

    /** Página do programa de breeders da própria pessoa: pedidos de representação e sementes cadastradas. */
    public BreederProgram program(UUID userId) {
        return new BreederProgram(queries.representationsOf(userId), queries.breederStrainsOf(userId));
    }

    public PageResult<RepresentativeRequest> representativeRequests(PageQuery page, boolean moderator) {
        requireModerator(moderator);
        return queries.representativeRequests(page);
    }

    public long pendingRepresentatives(boolean moderator) {
        requireModerator(moderator);
        return queries.pendingRepresentatives();
    }

    /** Banco aberto de strains (só agregados, sem dados pessoais). */
    public List<OpenStrain> openStrains() {
        return queries.openStrains();
    }

    public BreederListItem breeder(UUID id) {
        return queries.breeder(id).orElseThrow(() -> new ResourceNotFoundException("Breeder"));
    }

    public PageResult<BreederStrainCard> strainsOfBreeder(UUID breederId, PageQuery page) {
        breeder(breederId);
        return queries.strainsOfBreeder(breederId, page);
    }

    public PageResult<RankingRow> ranking(RankingKind kind, String category, PageQuery page) {
        return queries.ranking(kind, category, page);
    }

    /** Fila da moderação: cadastros da comunidade ainda não verificados. */
    public PageResult<PendingItem> pending(RankingKind kind, PageQuery page, boolean moderator) {
        requireModerator(moderator);
        return queries.pending(kind, page);
    }

    public Map<RankingKind, Long> pendingCounts(boolean moderator) {
        requireModerator(moderator);
        return queries.pendingCounts();
    }

    public record BreederProgram(List<Representation> representations, List<TestingStrainItem> strains) {
    }

    private static void requireModerator(boolean moderator) {
        if (!moderator) {
            throw new ModeratorOnlyException();
        }
    }

    private static boolean tooShort(String query) {
        return CatalogKey.of(query).length() < MIN_QUERY;
    }

    private static String normalizedOrNull(String query) {
        return CatalogKey.of(query).isEmpty() ? null : query;
    }
}
