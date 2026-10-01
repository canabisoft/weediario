package br.com.weediario.shared.domain;

import java.text.Normalizer;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filtro anti-comércio para textos públicos (Termos de Uso, cláusula de proibição de comércio).
 * <ul>
 *   <li><b>Bloqueia</b> o que é inequívoco: telefone, CPF/CNPJ, links de WhatsApp/Telegram e chave PIX.
 *       Nada é salvo e a pessoa recebe uma mensagem explicando.</li>
 *   <li><b>Retém para análise</b> combinações típicas de anúncio ("vendo 5g", "chama no zap", "faço entrega"):
 *       o conteúdo é salvo, fica invisível para o público e vai para a fila da moderação.</li>
 * </ul>
 * Não guarda nem registra o texto analisado. Os falsos positivos mais comuns do cultivo (pH, EC, gramas por
 * planta, custo de equipamento, datas) são permitidos de propósito.
 */
public final class CommerceFilter {

    public enum Decision {
        ALLOW,
        BLOCK,
        HOLD
    }

    /** @param reason motivo curto, mostrado para quem escreveu (bloqueio) ou para a moderação (retenção) */
    public record Verdict(Decision decision, String reason) {

        public static final Verdict ALLOWED = new Verdict(Decision.ALLOW, null);

        public boolean blocked() {
            return decision == Decision.BLOCK;
        }

        public boolean held() {
            return decision == Decision.HOLD;
        }
    }

    // ------------------------------------------------------------------------------------- bloqueio

    private static final Pattern MESSENGER_LINK = Pattern.compile(
            "(chat\\.whatsapp\\.com|wa\\.me/|api\\.whatsapp\\.com|whatsapp\\.com/|t\\.me/|telegram\\.me/|telegram\\.dog/)");
    /** Telefone com DDD (fixo ou celular), com ou sem +55 e separadores. */
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(\\+?55[\\s.-]?)?\\(?\\d{2}\\)?[\\s.-]?9?\\d{4}[\\s.-]?\\d{4}(?!\\d)");
    private static final Pattern CPF = Pattern.compile("(?<!\\d)\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}(?!\\d)");
    private static final Pattern CNPJ = Pattern.compile("(?<!\\d)\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}");
    private static final Pattern RANDOM_KEY = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern PIX = Pattern.compile("\\bpix\\b");

    // ------------------------------------------------------------------------------------- retenção

    /** Levar a conversa para fora do app. */
    private static final Pattern OFF_PLATFORM = Pattern.compile(
            "\\b(chama|me chama|manda|fala|chamar|add|adiciona)\\b.{0,15}\\b(zap|zapzap|whats|whatsapp|wpp|telegram|telegran|signal)\\b"
                    + "|\\b(zap|whats|wpp|telegram)\\b.{0,10}\\b(na bio|no perfil|no privado)\\b");
    private static final Pattern DELIVERY_OR_PAYMENT = Pattern.compile(
            "\\b(faco entrega|fazemos entrega|entrego|entregamos|delivery|frete gratis|envio para todo|mando pelo correio"
                    + "|aceito pix|aceito cartao|pix ou dinheiro)\\b");
    private static final Pattern SELL_VERB = Pattern.compile(
            "\\b(vendo|vende-se|vende|vendemos|a venda|compro|compramos|troco|trocamos|repasso|negocio)\\b");
    /** "estou vendo as folhas": gerúndio de ver, não venda. */
    private static final Pattern SEEING = Pattern.compile("\\b(estou|to|ta|esta|estamos|tamo|fico|ficou|tava|estava)\\s+vendo\\b");
    private static final Pattern PRODUCT = Pattern.compile(
            "\\b(gramas?|grs?|kg|prensado|haxixe|hash|kief|dry|ice|rosin|semente|sementes|clone|clones|estaca|estacas"
                    + "|muda|mudas|beck|becks|baseado|erva|flor|flores|buds?)\\b");
    private static final Pattern PRICE = Pattern.compile("(r\\$\\s?\\d+|\\b\\d+\\s?(reais|conto|contos|pila|pilas)\\b)");
    private static final Pattern QUANTITY = Pattern.compile("\\b\\d+[.,]?\\d*\\s?(g|gr|grs|gramas?|kg)\\b");

    private CommerceFilter() {
    }

    /** Analisa um ou mais campos do mesmo conteúdo (ex.: título e notas). Campos nulos são ignorados. */
    public static Verdict check(String... texts) {
        StringBuilder joined = new StringBuilder();
        for (String text : texts) {
            if (text != null && !text.isBlank()) {
                joined.append(text).append('\n');
            }
        }
        if (joined.isEmpty()) {
            return Verdict.ALLOWED;
        }
        String raw = joined.toString().toLowerCase();
        return block(raw).or(() -> hold(normalize(raw), raw)).orElse(Verdict.ALLOWED);
    }

    /** Lança {@link DomainException} se o texto for bloqueado; devolve o veredito caso contrário. */
    public static Verdict requireAllowedOrHeld(String... texts) {
        Verdict verdict = check(texts);
        if (verdict.blocked()) {
            throw new DomainException(blockMessage(verdict));
        }
        return verdict;
    }

    /** Para campos que não passam por moderação (ex.: nomes do catálogo): retenção também vira bloqueio. */
    public static void requireAllowed(String... texts) {
        Verdict verdict = check(texts);
        if (verdict.decision() != Decision.ALLOW) {
            throw new DomainException(blockMessage(verdict));
        }
    }

    public static String blockMessage(Verdict verdict) {
        return "Isso parece " + verdict.reason() + ". O weediário não permite comércio nem contato para negócio "
                + "(veja os Termos de Uso).";
    }

    private static Optional<Verdict> block(String text) {
        if (MESSENGER_LINK.matcher(text).find()) {
            return Optional.of(new Verdict(Decision.BLOCK, "um link de WhatsApp ou Telegram"));
        }
        if (PHONE.matcher(text).find() || CPF.matcher(text).find() || CNPJ.matcher(text).find()) {
            return Optional.of(new Verdict(Decision.BLOCK, "um telefone, CPF ou CNPJ"));
        }
        Matcher pix = PIX.matcher(text);
        while (pix.find()) {
            String around = text.substring(Math.max(0, pix.start() - 60), Math.min(text.length(), pix.end() + 60));
            if (EMAIL.matcher(around).find() || RANDOM_KEY.matcher(around).find()) {
                return Optional.of(new Verdict(Decision.BLOCK, "uma chave PIX"));
            }
        }
        return Optional.empty();
    }

    private static Optional<Verdict> hold(String text, String raw) {
        if (OFF_PLATFORM.matcher(text).find()) {
            return Optional.of(new Verdict(Decision.HOLD, "contato para fora do app"));
        }
        if (DELIVERY_OR_PAYMENT.matcher(text).find()) {
            return Optional.of(new Verdict(Decision.HOLD, "entrega ou forma de pagamento"));
        }
        if (EMAIL.matcher(raw).find()) {
            return Optional.of(new Verdict(Decision.HOLD, "e-mail de contato"));
        }
        String withoutSeeing = SEEING.matcher(text).replaceAll(" ");
        boolean sells = SELL_VERB.matcher(withoutSeeing).find();
        boolean price = PRICE.matcher(text).find();
        boolean quantity = QUANTITY.matcher(text).find();
        if (sells && (price || quantity || PRODUCT.matcher(text).find())) {
            return Optional.of(new Verdict(Decision.HOLD, "anúncio de venda ou troca"));
        }
        if (price && quantity) {
            return Optional.of(new Verdict(Decision.HOLD, "preço por quantidade"));
        }
        return Optional.empty();
    }

    private static final Pattern WORD = Pattern.compile("[a-z0-9@$]+");

    /**
     * Sem acento e com as trocas de letra por número mais comuns ("v3nd0" vira "vendo"). Só troca dentro de
     * palavras com pelo menos duas letras, para números de verdade ("5g", "200", "1.4") ficarem como estão.
     */
    static String normalize(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        Matcher words = WORD.matcher(plain);
        StringBuilder out = new StringBuilder(plain.length());
        while (words.find()) {
            String word = words.group();
            long letters = word.chars().filter(Character::isLetter).count();
            words.appendReplacement(out, Matcher.quoteReplacement(letters >= 2 ? unleet(word) : word));
        }
        words.appendTail(out);
        return out.toString();
    }

    private static String unleet(String word) {
        StringBuilder out = new StringBuilder(word.length());
        for (char c : word.toCharArray()) {
            out.append(switch (c) {
                case '0' -> 'o';
                case '1' -> 'i';
                case '3' -> 'e';
                case '4' -> 'a';
                case '5' -> 's';
                case '7' -> 't';
                case '@' -> 'a';
                default -> c;
            });
        }
        return out.toString();
    }
}
