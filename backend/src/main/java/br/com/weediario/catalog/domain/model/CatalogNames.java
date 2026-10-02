package br.com.weediario.catalog.domain.model;

import br.com.weediario.shared.domain.DomainException;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regras de nome do catálogo de genéticas. Um nome de strain é o nome de uma genética, não um anúncio:
 * <ul>
 *   <li>sem preço nem símbolo de moeda ("£25 to £50", "Petro $ OG");</li>
 *   <li>sem item de loja que não é genética: brinde ("3 Free Seeds"), pacote, mistura ou kit ("Mixed Pack",
 *   "Feminised Mix", "Box - 6 Strains"), roupa ou acessório ("Seed City Mug", "Seed Bank Clothing");</li>
 *   <li>sem ruído de vitrine: "SALE - X - Banco" vira "X"; "X Regular Seeds" vira "X".</li>
 * </ul>
 * Vale para todo cadastro novo (dataset, comunidade, programa de breeders). Registros antigos foram limpos na
 * migração V24.
 */
public final class CatalogNames {

    /** Símbolos de moeda (e "R$"). */
    private static final Pattern CURRENCY = Pattern.compile("[$€£¥₿¢]|(?i:\\br\\$)");
    /** Valor com moeda por extenso ("25 GBP", "10 euros", "50 reais"). */
    private static final Pattern PRICE_WORDS = Pattern.compile(
            "(?i)\\b\\d+(?:[.,]\\d+)?\\s*(?:gbp|usd|eur|brl|euros?|d[oó]lares|dollars?|reais|libras?)\\b");
    /** Brinde de loja: "1 Free Seed", "12 Free Seeds - ...", "sementes grátis". */
    private static final Pattern FREEBIE = Pattern.compile(
            "(?i)\\bfree\\s+seeds?\\b|\\bsementes?\\s+gr[aá]tis\\b|\\bbrinde\\b");
    /** Pacote ou kit com várias genéticas (não é uma genética). "Candy Packz" e "Gaspack" continuam válidos. */
    private static final Pattern PACK = Pattern.compile(
            "(?i)\\b(?:mix(?:ed)?\\s*pack|mixpack|mystery\\s+pack|collection\\s+pack|collectors?'?s?\\s+pack|champion\\s+pack"
                    + "|storage\\s+packs?|feminised\\s+collection|feminized\\s+collection|auto\\s+pack\\s*#?\\d*)\\b");
    /** Mistura de sementes ("Feminised Mix", "Tutti Frutti Feminized Mix") ou caixa com várias ("Box - 6 Strains"). */
    private static final Pattern MIX = Pattern.compile(
            "(?i)\\bfemini[sz]ed\\s+mix\\b|\\bmix\\s+femini[sz]ed\\b|\\b\\d+\\s+strains\\b|\\banniversary\\s+box\\b");
    /**
     * Produto de loja que não é semente (roupa, suporte) ou categoria de loja usada como breeder ("Seed Bank
     * Clothing"). Palavras que podem estar em nome de genética ("Pillow", "Dress") ficam de fora.
     */
    private static final Pattern MERCH = Pattern.compile(
            "(?i)\\b(?:t-?shirts?|tshirts?|sweatshirts?|hoodies?|crewneck|crop\\s+top|tote\\s+bag|drawstring\\s+bag"
                    + "|storage\\s+rack|clothing|apparel|merch(?:andise)?|camisetas?|canecas?)\\b");
    private static final Pattern SALE_PREFIX = Pattern.compile("(?i)^\\s*(?:sale|promo(?:[cç][aã]o)?|oferta)\\s*[-:–]\\s*");
    /** Sufixo de vitrine depois do nome: "Northern Lights Regular Seeds". */
    private static final Pattern SEED_SUFFIX = Pattern.compile(
            "(?i)\\s+(?:regular|femini[sz]ed|fem|autoflowering)\\s+seeds?\\s*$");

    private CatalogNames() {
    }

    /** Motivo pelo qual o nome não serve para o catálogo, se houver. */
    public static Optional<String> problemOf(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        if (CURRENCY.matcher(name).find() || PRICE_WORDS.matcher(name).find()) {
            return Optional.of("Nome de genética não pode ter preço nem símbolo de moeda");
        }
        if (FREEBIE.matcher(name).find()) {
            return Optional.of("Brinde de loja não é uma genética");
        }
        if (PACK.matcher(name).find() || MIX.matcher(name).find()) {
            return Optional.of("Pacote, mistura ou kit com várias sementes não é uma genética");
        }
        if (MERCH.matcher(name).find()) {
            return Optional.of("Produto de loja (roupa, acessório) não é uma genética");
        }
        if (SALE_PREFIX.matcher(name).find()) {
            return Optional.of("Nome de genética não pode ter anúncio (\"SALE\", \"promoção\")");
        }
        return Optional.empty();
    }

    /** Para cadastros novos. Não é chamado ao reconstituir registros do banco. */
    public static void requireValid(String name) {
        problemOf(name).ifPresent(problem -> {
            throw new DomainException(problem);
        });
    }

    /**
     * Tira o ruído de vitrine que lojas põem no nome: "SALE - X - Banco" → "X"; "X Regular Seeds" → "X". Se o
     * breeder vier embutido no fim ("X - Banco"), devolve também o nome dele.
     *
     * @param knownBreeder breeder já informado na linha (para reconhecer e cortar "X - Banco"); pode ser nulo
     */
    public static Cleaned stripStoreNoise(String raw, String knownBreeder) {
        if (raw == null) {
            return new Cleaned(null, null);
        }
        String name = raw.strip();
        boolean sale = SALE_PREFIX.matcher(name).find();
        name = SALE_PREFIX.matcher(name).replaceFirst("");
        String breeder = null;
        if (sale) {
            // "X - Banco" (às vezes repetido ou truncado: "X - Banco - Banco - Ban"): o primeiro trecho é o nome.
            String[] parts = name.split("\\s+-\\s*");
            if (parts.length >= 3 && looksLikeBreeder(parts[0])) {
                // "SALE - Banco - X - Loja"
                breeder = parts[0].strip();
                name = parts[1].strip();
            } else {
                name = parts[0].strip();
                if (parts.length > 1 && !parts[1].isBlank()) {
                    breeder = parts[1].strip();
                }
            }
        } else if (knownBreeder != null && !knownBreeder.isBlank()) {
            Matcher suffix = Pattern.compile("\\s+-\\s+" + Pattern.quote(knownBreeder.strip()) + "\\s*$",
                    Pattern.CASE_INSENSITIVE).matcher(name);
            name = suffix.replaceFirst("");
        }
        name = SEED_SUFFIX.matcher(name).replaceFirst("").strip();
        return new Cleaned(name, breeder);
    }

    /**
     * "Banco - Strain" (formato de algumas lojas): separa quando o prefixo parece nome de breeder.
     *
     * @return vazio se não estiver nesse formato
     */
    public static Optional<Cleaned> splitBreederPrefix(String raw, boolean prefixIsKnownBreeder) {
        if (raw == null) {
            return Optional.empty();
        }
        String[] parts = raw.strip().split("\\s+-\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            return Optional.empty();
        }
        String prefix = parts[0].strip();
        return prefixIsKnownBreeder || looksLikeBreeder(prefix)
                ? Optional.of(new Cleaned(parts[1].strip(), prefix))
                : Optional.empty();
    }

    private static boolean looksLikeBreeder(String text) {
        return text.strip().toLowerCase(Locale.ROOT).matches(".*\\b(seeds?|genetics|labs?|seedbank|seed co\\.?)$");
    }

    /** @param breeder breeder encontrado no próprio nome (nulo se não havia) */
    public record Cleaned(String name, String breeder) {
    }
}
