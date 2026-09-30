package br.com.weediario.catalog.infrastructure.dataset;

import br.com.weediario.catalog.application.usecase.ImportDatasetUseCase.DatasetStrain;
import br.com.weediario.catalog.domain.model.CatalogEnums.Dominance;
import br.com.weediario.catalog.domain.model.CatalogEnums.FloweringKind;
import br.com.weediario.catalog.domain.model.CatalogEnums.FloweringSpeed;
import br.com.weediario.catalog.domain.model.CatalogEnums.SeedKind;
import br.com.weediario.catalog.domain.model.StrainTraits;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Interpreta o dataset público de strains (cannabis-strains-final.csv).
 * <p>
 * Só entram atributos factuais e confiáveis. Ficam de fora, por decisão consciente:
 * <ul>
 *   <li>textos de descrição/overview (modelos repetidos e possivelmente protegidos por direito autoral);</li>
 *   <li>THC, CBD, produtividade e altura (a maioria é valor de preenchimento, ex.: "20%" em ~70% das linhas);</li>
 *   <li>genética "Northern Lights x Haze", que é o valor padrão do dataset (~90% das linhas);</li>
 *   <li>valores de preenchimento de sabor/efeito quando aparecem sozinhos: flavor "Citrus" (~87% das linhas),
 *       effect "Relaxing" (~76%) e smell_taste "Sweet" (~29%);</li>
 *   <li>preços, links e estoque (dados de loja).</li>
 * </ul>
 * Sabores e efeitos são traduzidos para termos canônicos em português; termos desconhecidos são descartados.
 */
public final class DatasetCsvReader {

    /** Categorias da loja que aparecem no campo "breeder" mas não são breeders. */
    private static final Set<String> FAKE_BREEDERS = Set.of(
            "cannabis seed sale items", "seed city bulk cannabis seeds", "seed city seeds", "free seed offer");

    private static final String FILLER_GENETICS = "northern lights x haze";
    private static final String FILLER_FLAVOR = "citrus";
    private static final String FILLER_SMELL = "sweet";
    private static final String FILLER_EFFECT = "relaxing";

    private static final Map<String, String> FLAVORS = Map.ofEntries(
            Map.entry("citrus", "cítrico"), Map.entry("citric", "cítrico"), Map.entry("citric fruit", "cítrico"),
            Map.entry("sweet", "doce"), Map.entry("candy", "doce"), Map.entry("sweet fruit", "frutado"),
            Map.entry("fruity", "frutado"), Map.entry("fruit", "frutado"), Map.entry("ripe fruits", "frutado"),
            Map.entry("tropical", "tropical"), Map.entry("earthy", "terroso"), Map.entry("earth", "terroso"),
            Map.entry("spicy", "picante"), Map.entry("peppery", "apimentado"), Map.entry("pepper", "apimentado"),
            Map.entry("berry", "frutas vermelhas"), Map.entry("raspberry", "frutas vermelhas"),
            Map.entry("blueberry", "mirtilo"), Map.entry("strawberry", "morango"), Map.entry("cherry", "cereja"),
            Map.entry("lemon", "limão"), Map.entry("lime", "limão"), Map.entry("orange", "laranja"),
            Map.entry("grapefruit", "toranja"), Map.entry("grape", "uva"), Map.entry("pineapple", "abacaxi"),
            Map.entry("banana", "banana"), Map.entry("mango", "manga"), Map.entry("melon", "melão"),
            Map.entry("watermelon", "melancia"), Map.entry("piney", "pinho"), Map.entry("pine", "pinho"),
            Map.entry("gassy", "combustível"), Map.entry("fuel", "combustível"), Map.entry("diesel", "diesel"),
            Map.entry("sour", "ácido"), Map.entry("tangy", "ácido"), Map.entry("skunky", "skunk"),
            Map.entry("skunk", "skunk"), Map.entry("dairy", "queijo"), Map.entry("cheese", "queijo"),
            Map.entry("floral", "floral"), Map.entry("flowery", "floral"), Map.entry("woodsy", "amadeirado"),
            Map.entry("woody", "amadeirado"), Map.entry("noble wood", "amadeirado"), Map.entry("sandalwood", "sândalo"),
            Map.entry("hashy", "haxixe"), Map.entry("herbal", "herbal"), Map.entry("minty", "menta"),
            Map.entry("mint", "menta"), Map.entry("vanilla", "baunilha"), Map.entry("musky", "almiscarado"),
            Map.entry("musty", "almiscarado"), Map.entry("incense", "incenso"), Map.entry("incensed", "incenso"),
            Map.entry("chocolate", "chocolate"), Map.entry("chemical", "químico"), Map.entry("coffee", "café"),
            Map.entry("cake", "bolo"), Map.entry("cookies", "biscoito"), Map.entry("cookie", "biscoito"),
            Map.entry("nutty", "castanhas"), Map.entry("caramel", "caramelo"), Map.entry("honey", "mel"),
            Map.entry("cinnamon", "canela"), Map.entry("bitter", "amargo"), Map.entry("liquorice", "alcaçuz"),
            Map.entry("anise", "anis"), Map.entry("metallic", "metálico"), Map.entry("pungent", "pungente"),
            Map.entry("creamy", "cremoso"), Map.entry("kush", "kush"), Map.entry("haze", "haze"),
            Map.entry("lavender", "lavanda"), Map.entry("earl gray tea", "chá"), Map.entry("tea", "chá"));

    private static final Map<String, String> EFFECTS = Map.ofEntries(
            Map.entry("relaxing", "relaxante"), Map.entry("relaxed", "relaxante"), Map.entry("relaxation", "relaxante"),
            Map.entry("physically relaxing", "relaxante"), Map.entry("mellow", "relaxante"),
            Map.entry("calming", "calmante"), Map.entry("soothing", "calmante"), Map.entry("chill", "calmante"),
            Map.entry("euphoric", "eufórico"), Map.entry("euphoria", "eufórico"),
            Map.entry("creative", "criativo"), Map.entry("creativity", "criativo"),
            Map.entry("energetic", "energético"), Map.entry("energizing", "energético"),
            Map.entry("stimulating", "energético"), Map.entry("active", "energético"),
            Map.entry("motivating", "motivador"), Map.entry("sleepy", "sonolento"), Map.entry("sedative", "sonolento"),
            Map.entry("narcotic", "sonolento"), Map.entry("couch lock", "sonolento"),
            Map.entry("uplifting", "animado"), Map.entry("uplifted", "animado"),
            Map.entry("focused", "focado"), Map.entry("focus", "focado"), Map.entry("clear", "lúcido"),
            Map.entry("clear high", "lúcido"), Map.entry("powerful", "potente"), Map.entry("strong", "potente"),
            Map.entry("potent", "potente"), Map.entry("heavy", "potente"), Map.entry("heavy hitter", "potente"),
            Map.entry("cerebral", "cerebral"), Map.entry("head", "cerebral"), Map.entry("happy", "feliz"),
            Map.entry("happiness", "feliz"), Map.entry("well-being", "bem-estar"),
            Map.entry("physical", "corporal"), Map.entry("body", "corporal"), Map.entry("stoned", "chapado"),
            Map.entry("physically stoned", "chapado"), Map.entry("long-lasting", "duradouro"),
            Map.entry("long lasting", "duradouro"), Map.entry("balanced", "equilibrado"),
            Map.entry("well balanced", "equilibrado"), Map.entry("medicinal", "medicinal"),
            Map.entry("medical", "medicinal"), Map.entry("psychedelic", "psicodélico"),
            Map.entry("talkative", "falante"), Map.entry("social", "social"), Map.entry("sociable", "social"),
            Map.entry("laughter", "risonho"), Map.entry("giggly", "risonho"), Map.entry("giggles", "risonho"),
            Map.entry("hungry", "fome"), Map.entry("meditative", "meditativo"));

    private DatasetCsvReader() {
    }

    public static List<DatasetStrain> read(Reader reader) throws IOException {
        List<DatasetStrain> rows = new ArrayList<>();
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).get();
        for (CSVRecord record : format.parse(reader)) {
            rows.add(new DatasetStrain(
                    record.get("strain_name"),
                    breeder(record.get("breeder")),
                    new StrainTraits(
                            flowering(record.get("flowering_period_type")),
                            dominance(record.get("indica_sativa")),
                            speed(record.get("indoor_flowering_time")),
                            seedTypes(record.get("seed_type")),
                            tags(FLAVORS, unlessFiller(record.get("smell_taste"), FILLER_SMELL),
                                    unlessFiller(record.get("flavor"), FILLER_FLAVOR)),
                            tags(EFFECTS, unlessFiller(record.get("effect"), FILLER_EFFECT)),
                            genetics(record.get("genetic_background")))));
        }
        return rows;
    }

    static String breeder(String raw) {
        return raw == null || raw.isBlank() || FAKE_BREEDERS.contains(raw.strip().toLowerCase(Locale.ROOT)) ? null : raw.strip();
    }

    /** Valores combinados ("12/12 Photoperiod , Autoflowering") são ambíguos: ficam vazios. */
    static FloweringKind flowering(String raw) {
        return single(raw, value -> value.contains("auto") ? FloweringKind.AUTOFLOWER
                : value.contains("photoperiod") ? FloweringKind.PHOTOPERIOD : null);
    }

    static Dominance dominance(String raw) {
        return single(raw, value -> switch (value) {
            case "indica" -> Dominance.INDICA;
            case "sativa" -> Dominance.SATIVA;
            case "indica dominant" -> Dominance.INDICA_DOMINANT;
            case "sativa dominant" -> Dominance.SATIVA_DOMINANT;
            case "50% indica/50% sativa" -> Dominance.HYBRID;
            default -> null;
        });
    }

    static FloweringSpeed speed(String raw) {
        return single(raw.replace("), ", ");"), value -> value.startsWith("short") ? FloweringSpeed.SHORT
                : value.startsWith("medium") ? FloweringSpeed.MEDIUM
                : value.startsWith("long") ? FloweringSpeed.LONG : null);
    }

    static Set<SeedKind> seedTypes(String raw) {
        Set<SeedKind> kinds = EnumSet.noneOf(SeedKind.class);
        String value = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        if (value.contains("feminized") || value.contains("feminised")) {
            kinds.add(SeedKind.FEMINIZED);
        }
        if (value.contains("regular")) {
            kinds.add(SeedKind.REGULAR);
        }
        return kinds;
    }

    /** Descarta o campo quando ele é exatamente o valor de preenchimento do dataset. */
    static String unlessFiller(String raw, String filler) {
        return raw == null || raw.strip().equalsIgnoreCase(filler) ? null : raw;
    }

    static String genetics(String raw) {
        if (raw == null || raw.isBlank() || raw.strip().equalsIgnoreCase(FILLER_GENETICS)
                || raw.length() > StrainTraits.GENETICS_MAX_LENGTH) {
            return null;
        }
        return raw.strip();
    }

    /** Traduz termos; compostos desconhecidos ("sweet earthy flowery") são traduzidos palavra por palavra. */
    static List<String> tags(Map<String, String> dictionary, String... fields) {
        Set<String> result = new LinkedHashSet<>();
        for (String field : fields) {
            if (field == null) {
                continue;
            }
            for (String term : field.toLowerCase(Locale.ROOT).split(",")) {
                String cleaned = term.replaceAll("[.!]", "").strip();
                String translated = dictionary.get(cleaned);
                if (translated != null) {
                    result.add(translated);
                } else {
                    for (String word : cleaned.split("\\s+")) {
                        String single = dictionary.get(word);
                        if (single != null) {
                            result.add(single);
                        }
                    }
                }
            }
        }
        return result.stream().limit(StrainTraits.MAX_TAGS).toList();
    }

    private static <T> T single(String raw, Function<String, T> parser) {
        if (raw == null || raw.isBlank() || raw.contains(";") || raw.contains(" , ")) {
            return null;
        }
        return parser.apply(raw.strip().toLowerCase(Locale.ROOT));
    }
}
