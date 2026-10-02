package br.com.weediario.catalog.presentation.rest;

import br.com.weediario.catalog.application.CatalogItem;
import br.com.weediario.catalog.application.Resolution;
import br.com.weediario.catalog.application.query.BrowseCatalogUseCase;
import br.com.weediario.catalog.application.query.CatalogQueries.BreederListItem;
import br.com.weediario.catalog.application.query.CatalogQueries.BreederStrainCard;
import br.com.weediario.catalog.application.query.CatalogQueries.ProductListItem;
import br.com.weediario.catalog.application.query.CatalogQueries.ProductReview;
import br.com.weediario.catalog.application.query.CatalogQueries.RankingKind;
import br.com.weediario.catalog.application.query.CatalogQueries.RankingRow;
import br.com.weediario.catalog.application.query.CatalogQueries.StrainDetail;
import br.com.weediario.catalog.application.query.CatalogQueries.StrainListItem;
import br.com.weediario.catalog.application.query.CatalogQueries.Suggestion;
import br.com.weediario.catalog.application.query.CatalogQueries.TestingStrainItem;
import br.com.weediario.catalog.application.usecase.CurateCatalogUseCase;
import br.com.weediario.catalog.application.usecase.CurateCatalogUseCase.Kind;
import br.com.weediario.catalog.application.usecase.ResolveCatalogUseCase;
import br.com.weediario.catalog.domain.model.CatalogEnums.Dominance;
import br.com.weediario.catalog.domain.model.CatalogEnums.FloweringKind;
import br.com.weediario.catalog.domain.model.CatalogEnums.FloweringSpeed;
import br.com.weediario.catalog.domain.model.CatalogEnums.ProductCategory;
import br.com.weediario.catalog.domain.model.CatalogEnums.SeedKind;
import br.com.weediario.catalog.domain.model.CatalogEnums.StrainGeneration;
import br.com.weediario.catalog.domain.model.CatalogStrain;
import br.com.weediario.catalog.domain.model.StrainTraits;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.presentation.Viewer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Catálogo da comunidade. Consultas são abertas a visitantes; cadastrar, completar e moderar exigem login.
 */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private static final Map<String, Kind> KINDS = Map.of(
            "strains", Kind.STRAIN, "breeders", Kind.BREEDER, "brands", Kind.BRAND, "products", Kind.PRODUCT);

    private final BrowseCatalogUseCase browse;
    private final ResolveCatalogUseCase resolve;
    private final CurateCatalogUseCase curate;

    public CatalogController(BrowseCatalogUseCase browse, ResolveCatalogUseCase resolve, CurateCatalogUseCase curate) {
        this.browse = browse;
        this.resolve = resolve;
        this.curate = curate;
    }

    // --------------------------------------------------------------------------------- autocomplete

    @GetMapping("/strains/suggest")
    public List<Suggestion> suggestStrains(@RequestParam @Size(max = 80) String q,
                                           @RequestParam(required = false) UUID breederId) {
        return browse.suggestStrains(q, breederId);
    }

    @GetMapping("/breeders/suggest")
    public List<Suggestion> suggestBreeders(@RequestParam @Size(max = 80) String q) {
        return browse.suggestBreeders(q);
    }

    @GetMapping("/brands/suggest")
    public List<Suggestion> suggestBrands(@RequestParam @Size(max = 80) String q) {
        return browse.suggestBrands(q);
    }

    @GetMapping("/products/suggest")
    public List<Suggestion> suggestProducts(@RequestParam @Size(max = 80) String q,
                                            @RequestParam(required = false) UUID brandId,
                                            @RequestParam(required = false) ProductCategory category) {
        return browse.suggestProducts(q, brandId, category == null ? null : category.name());
    }

    // ------------------------------------------------------------------------------------ listagens

    @GetMapping("/strains")
    public PageResult<StrainListItem> strains(@RequestParam(required = false) @Size(max = 80) String q,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return browse.strains(q, new PageQuery(page, size));
    }

    @GetMapping("/strains/{id}")
    public StrainDetail strain(@PathVariable UUID id) {
        return browse.strain(id);
    }

    /** Sementes de breeders em teste pela comunidade (fora da listagem oficial). */
    @GetMapping("/strains/testing")
    public PageResult<TestingStrainItem> testingStrains(@RequestParam(required = false) @Size(max = 80) String q,
                                                        @RequestParam(required = false) UUID breederId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return browse.testingStrains(q, breederId, new PageQuery(page, size));
    }

    @GetMapping("/breeders")
    public PageResult<BreederListItem> breeders(@RequestParam(required = false) @Size(max = 80) String q,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return browse.breeders(q, new PageQuery(page, size));
    }

    @GetMapping("/breeders/{id}")
    public BreederListItem breeder(@PathVariable UUID id) {
        return browse.breeder(id);
    }

    @GetMapping("/breeders/{id}/strains")
    public PageResult<BreederStrainCard> breederStrains(@PathVariable UUID id,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "24") int size) {
        return browse.strainsOfBreeder(id, new PageQuery(page, size));
    }

    @GetMapping("/products")
    public PageResult<ProductListItem> products(@RequestParam(required = false) @Size(max = 80) String q,
                                                @RequestParam(required = false) ProductCategory category,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return browse.products(q, category == null ? null : category.name(), new PageQuery(page, size));
    }

    @GetMapping("/products/{id}")
    public ProductListItem product(@PathVariable UUID id) {
        return browse.product(id);
    }

    /** Notas do produto nas reviews pós-colheita, mais recentes primeiro (sem autor). */
    @GetMapping("/products/{id}/reviews")
    public PageResult<ProductReview> productReviews(@PathVariable UUID id,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return browse.productReviews(id, new PageQuery(page, size));
    }

    /** "Mais usados": strains, breeders, marcas ou produtos (opcionalmente por categoria de produto). */
    @GetMapping("/rankings/{kind}")
    public PageResult<RankingRow> ranking(@PathVariable RankingKind kind,
                                          @RequestParam(required = false) ProductCategory category,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return browse.ranking(kind, category == null ? null : category.name(), new PageQuery(page, size));
    }

    // ------------------------------------------------------------------------ achar ou cadastrar

    @PostMapping("/strains/resolve")
    public Resolution resolveStrain(@AuthenticationPrincipal Jwt principal, @Valid @RequestBody StrainResolveRequest request) {
        return resolve.resolveStrain(request.name(), request.breederId(), request.confirmNew(), Viewer.requireId(principal));
    }

    @PostMapping("/breeders/resolve")
    public Resolution resolveBreeder(@AuthenticationPrincipal Jwt principal, @Valid @RequestBody NameResolveRequest request) {
        return resolve.resolveBreeder(request.name(), request.confirmNew(), Viewer.requireId(principal));
    }

    @PostMapping("/brands/resolve")
    public Resolution resolveBrand(@AuthenticationPrincipal Jwt principal, @Valid @RequestBody NameResolveRequest request) {
        return resolve.resolveBrand(request.name(), request.confirmNew(), Viewer.requireId(principal));
    }

    @PostMapping("/products/resolve")
    public Resolution resolveProduct(@AuthenticationPrincipal Jwt principal,
                                     @Valid @RequestBody ProductResolveRequest request) {
        return resolve.resolveProduct(request.brandId(), request.name(), request.category(), request.confirmNew(),
                Viewer.requireId(principal));
    }

    // ------------------------------------------------------------------------------- curadoria

    /** Completa informações que faltam (moderadores podem corrigir tudo). */
    @PatchMapping("/strains/{id}")
    public Map<String, Boolean> contribute(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id,
                                           @Valid @RequestBody TraitsRequest request) {
        return Map.of("changed", curate.contribute(id, request.traits(), Viewer.isModerator(principal)));
    }

    @PostMapping("/{kind}/{id}/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@AuthenticationPrincipal Jwt principal, @PathVariable String kind, @PathVariable UUID id) {
        curate.verify(kindOf(kind), id, Viewer.isModerator(principal));
    }

    /** Junta o item {@code id} (duplicado) no {@code targetId} (principal). */
    @PostMapping("/{kind}/{id}/merge")
    public CatalogItem merge(@AuthenticationPrincipal Jwt principal, @PathVariable String kind, @PathVariable UUID id,
                             @Valid @RequestBody MergeRequest request) {
        return curate.merge(kindOf(kind), id, request.targetId(), Viewer.isModerator(principal));
    }

    /** Recusa um cadastro que ninguém usa (erro de digitação, spam). */
    @DeleteMapping("/{kind}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@AuthenticationPrincipal Jwt principal, @PathVariable String kind, @PathVariable UUID id) {
        curate.reject(kindOf(kind), id, Viewer.isModerator(principal));
    }

    static Kind kindOf(String path) {
        Kind parsed = KINDS.get(path);
        if (parsed == null) {
            throw new ResourceNotFoundException("Tipo de item");
        }
        return parsed;
    }

    // ------------------------------------------------------------------------------------ DTOs

    public record NameResolveRequest(@NotBlank @Size(max = CatalogStrain.NAME_MAX_LENGTH) String name, boolean confirmNew) {
    }

    public record StrainResolveRequest(@NotBlank @Size(max = CatalogStrain.NAME_MAX_LENGTH) String name, UUID breederId,
                                       boolean confirmNew) {
    }

    public record ProductResolveRequest(@NotNull UUID brandId, @NotBlank @Size(max = CatalogStrain.NAME_MAX_LENGTH) String name,
                                        ProductCategory category, boolean confirmNew) {
    }

    public record MergeRequest(@NotNull UUID targetId) {
    }

    public record TraitsRequest(
            FloweringKind floweringType,
            Dominance dominance,
            FloweringSpeed floweringSpeed,
            Set<SeedKind> seedTypes,
            @Size(max = StrainTraits.MAX_TAGS) List<@Size(max = StrainTraits.TAG_MAX_LENGTH) String> flavors,
            @Size(max = StrainTraits.MAX_TAGS) List<@Size(max = StrainTraits.TAG_MAX_LENGTH) String> effects,
            @Size(max = StrainTraits.GENETICS_MAX_LENGTH) String genetics,
            StrainGeneration generation) {

        public StrainTraits traits() {
            return new StrainTraits(floweringType, dominance, floweringSpeed, seedTypes, flavors, effects, genetics,
                    generation);
        }
    }
}
