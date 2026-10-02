package br.com.weediario.catalog.presentation.rest;

import br.com.weediario.catalog.application.query.BrowseCatalogUseCase;
import br.com.weediario.catalog.application.query.CatalogQueries.OpenStrain;
import br.com.weediario.shared.presentation.Csv;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * Banco aberto de strains, público e sem login, sob a licença ODbL 1.0. Só strains oficiais (as sementes em teste
 * ficam de fora) e só agregados: nenhum apelido, id de diário, id de review ou qualquer dado pessoal.
 */
@RestController
@RequestMapping("/api/v1/open-data")
public class OpenDataController {

    public static final String LICENSE = "ODbL-1.0";
    public static final String LICENSE_URL = "https://opendatacommons.org/licenses/odbl/1-0/";
    public static final String ATTRIBUTION = "Banco aberto de strains do weediário, construído pela comunidade de growers";
    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();
    private static final String LIST_SEPARATOR = "|";

    private static final String CSV_HEADER = String.join(",", "id", "name", "breeder", "flowering_type", "dominance",
            "flowering_speed", "seed_types", "flavors", "effects", "genetics", "status", "source", "promoted_at",
            "public_diaries", "growers", "review_count", "vigor", "pest_resistance", "ease_of_growth", "yield",
            "terpene_profile", "overall", "avg_grams_per_plant", "breeder_kind", "generation");

    private final BrowseCatalogUseCase browse;
    private final Clock clock;

    public OpenDataController(BrowseCatalogUseCase browse, Clock clock) {
        this.browse = browse;
        this.clock = clock;
    }

    @GetMapping(value = "/strains", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OpenStrainsDump> strains() {
        List<OpenStrain> strains = browse.openStrains();
        return ResponseEntity.ok()
                .cacheControl(CACHE)
                .header(HttpHeaders.LINK, licenseLink())
                .body(new OpenStrainsDump(LICENSE, LICENSE_URL, ATTRIBUTION, clock.instant(), strains.size(), strains));
    }

    @GetMapping("/strains.csv")
    public ResponseEntity<byte[]> strainsCsv() {
        StringBuilder csv = new StringBuilder(CSV_HEADER).append('\n');
        for (OpenStrain s : browse.openStrains()) {
            csv.append(String.join(",", Stream.of(
                    Csv.value(s.id()), Csv.cell(s.name()), Csv.cell(s.breeder()), Csv.value(s.floweringType()),
                    Csv.value(s.dominance()), Csv.value(s.floweringSpeed()), list(s.seedTypes()), list(s.flavors()),
                    list(s.effects()), Csv.cell(s.genetics()), Csv.value(s.status()), Csv.value(s.source()),
                    Csv.value(s.promotedAt()), Csv.value(s.publicDiaries()), Csv.value(s.growers()),
                    Csv.value(s.reviewCount()), Csv.value(s.vigor()), Csv.value(s.pestResistance()),
                    Csv.value(s.easeOfGrowth()), Csv.value(s.yield()), Csv.value(s.terpeneProfile()),
                    Csv.value(s.overall()), Csv.value(s.avgGramsPerPlant()), Csv.value(s.breederKind()),
                    Csv.value(s.generation())).toList())).append('\n');
        }
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .cacheControl(CACHE)
                .header(HttpHeaders.LINK, licenseLink())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("weediario-strains.csv").build().toString())
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String list(List<String> values) {
        return values.isEmpty() ? "" : Csv.cell(String.join(LIST_SEPARATOR, values));
    }

    private static String licenseLink() {
        return "<" + LICENSE_URL + ">; rel=\"license\"";
    }

    /**
     * @param license     identificador SPDX da licença dos dados
     * @param attribution como citar a fonte ao reutilizar
     */
    public record OpenStrainsDump(String license, String licenseUrl, String attribution, Instant generatedAt,
                                  int count, List<OpenStrain> strains) {
    }
}
