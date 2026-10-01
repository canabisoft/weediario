package br.com.weediario.shared.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * Também pega os disfarces mais comuns: letras espaçadas ou pontuadas ("p.i.x", "z a p"), leetspeak ("v3nd0"),
 * letra no lugar de dígito ("98765-432l"), telefone com separadores estranhos ("11 9 8765 4321"), e-mail escrito
 * por extenso ("fulano arroba gmail ponto com") e link com espaço ("wa . me/").
 * <p>
 * Não guarda nem registra o texto analisado. Os falsos positivos mais comuns do cultivo (pH, EC, PPFD, gramas por
 * planta, custo de equipamento, datas, fotoperíodo, modelos de LED, strains com número) são permitidos de propósito.
 * Na dúvida, retém (a moderação decide); bloqueio só quando não há outra leitura possível.
 * <p>
 * Todas as expressões são pré-compiladas e lineares (repetições limitadas ou possessivas): um texto de 10 mil
 * caracteres é analisado em poucos milissegundos.
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

    // ------------------------------------------------------------------------------------- motivos

    private static final String REASON_MESSENGER_LINK = "um link de WhatsApp, Telegram ou outro mensageiro";
    private static final String REASON_PHONE = "um telefone, CPF ou CNPJ";
    private static final String REASON_PIX = "uma chave PIX";
    private static final String REASON_MAYBE_PHONE = "um possível telefone";
    private static final String REASON_MAYBE_PIX = "uma possível chave PIX";
    private static final String REASON_SHORT_LINK = "um link encurtado";
    private static final String REASON_OFF_PLATFORM = "contato para fora do app";
    private static final String REASON_PAYMENT = "entrega ou forma de pagamento";
    private static final String REASON_EMAIL = "e-mail de contato";
    private static final String REASON_AD = "anúncio de venda ou troca";
    private static final String REASON_PRICE = "preço por quantidade";

    // ------------------------------------------------------------------------------------- disfarces

    /** Até três separadores entre as letras de uma palavra disfarçada ("p.i.x", "z a p", "w-p-p", "p_i_x"). */
    private static final String SEP = "[\\s.\\-_*|/\\\\,;:'\"`~+()\\[\\]{}!?]{0,3}";

    /** "pix" também como "p i x", "p.i.x", "piiix", "piks", "pyx". */
    private static final String PIX_WORD = "(?:" + spaced("pix") + "|" + spaced("piks") + "|" + spaced("pyx")
            + "|" + spaced("pixs") + ")";
    /** "pics" quase sempre é "fotos" no cultivo; só conta como PIX ambíguo (retenção) quando há uma chave perto. */
    private static final String PICS_WORD = "(?:" + spaced("pics") + "|" + spaced("picks") + ")";
    /** Nomes de mensageiro ("zap", "z a p", "zaap", "whats", "wpp", "telegram"). Sem "watts", que é unidade. */
    private static final String MESSENGER_WORD = "(?:" + spaced("zapzap") + "|" + spaced("zap") + "|"
            + spaced("whatsap") + "|" + spaced("whatzap") + "|" + spaced("whats") + "|" + spaced("wpp") + "|"
            + spaced("telegram") + "|" + spaced("telegran") + ")";

    // ------------------------------------------------------------------------------------- bloqueio

    /** Procurado na versão "de link" do texto (sem espaço em volta de ponto e barra, "ponto"/"dot" viram "."). */
    private static final Pattern MESSENGER_LINK = Pattern.compile(
            "\\b(?:wa\\.me/|wa\\.link/|api\\.whatsapp\\.com|chat\\.whatsapp\\.com|web\\.whatsapp\\.com|whatsapp\\.com/"
                    + "|t\\.me/|telegram\\.me/|telegram\\.dog/|tg://|signal\\.me/|signal\\.group/|discord\\.gg/"
                    + "|discord\\.com/invite)");
    /** CPF e CNPJ formatados (ou 11/14 dígitos seguidos): inequívocos mesmo sem dígito verificador válido. */
    private static final Pattern CPF = Pattern.compile("(?<!\\d)\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}(?!\\d)");
    private static final Pattern CNPJ = Pattern.compile("(?<!\\d)\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}(?!\\d)");
    /** E-mail com tamanhos limitados e início ancorado: sem varredura quadrática em textos longos. */
    private static final Pattern EMAIL = Pattern.compile(
            "(?<![a-z0-9._%+\\-])[a-z0-9._%+\\-]{1,64}@[a-z0-9\\-]{1,63}(?:\\.[a-z0-9\\-]{1,63}){0,4}\\.[a-z]{2,24}");
    /** "fulano arroba gmail ponto com", "fulano [at] gmail [dot] com", "fulano @ gmail . com". */
    private static final Pattern SPELLED_EMAIL = Pattern.compile(
            "(?<![a-z0-9._\\-])[a-z0-9._\\-]{2,64}\\s{0,3}(?:@|\\barroba\\b|[(\\[{]\\s{0,2}(?:at|arroba)\\s{0,2}[)\\]}])"
                    + "\\s{0,3}[a-z0-9\\-]{2,63}\\s{0,3}(?:\\.|\\bponto\\b|\\bdot\\b|[(\\[{]\\s{0,2}(?:\\.|dot|ponto)\\s{0,2}[)\\]}])"
                    + "\\s{0,3}(?:com|net|org|br|me|io)\\b");
    /** Chave aleatória (EVP): UUID com ou sem hífens/espaços. */
    private static final Pattern RANDOM_KEY = Pattern.compile(
            "(?<![0-9a-f])[0-9a-f]{8}(?:[\\s-]?[0-9a-f]{4}){3}[\\s-]?[0-9a-f]{12}(?![0-9a-f])");
    private static final Pattern PIX = startingWith("p", "\\b" + PIX_WORD + "\\b");
    private static final Pattern PICS = startingWith("p", "\\b" + PICS_WORD + "\\b");
    /** "chave aleatória: ...", "chave pix ...", "chave: ...". */
    private static final Pattern KEY_WORD = startingWith("c", "\\bchave\\s{0,3}(?:aleatoria|evp|" + PIX_WORD + "|:)");
    private static final Pattern MESSENGER = startingWith("zwt", "\\b" + MESSENGER_WORD + "\\b");
    /** Sequência de dígitos com separadores curtos entre os grupos ("(11) 9 8765-4321", "1 1 9 8 7..."). */
    private static final Pattern DIGIT_RUN = Pattern.compile("\\d++(?:[\\s.\\-_/()+*]{1,3}+\\d++)*+");
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}.*", Pattern.DOTALL);

    // ------------------------------------------------------------------------------------- retenção

    private static final Pattern SHORT_LINK = Pattern.compile(
            "\\b(?:bit\\.ly|tinyurl\\.com|cutt\\.ly|encurtador\\.com\\.br|linktr\\.ee|is\\.gd|rebrand\\.ly|shorturl\\.at"
                    + "|t\\.co|goo\\.gl|abre\\.ai|beacons\\.ai|tiny\\.cc|ow\\.ly)/");
    private static final String CONTACT_VERB = "(?:chama|chame|chamem|chamar|me chama|manda|mande|me manda|fala|fale"
            + "|falar|add|me add|adiciona|adicione|entra|entre|contato|contatos|bate|me procura|procura|procure|me acha)";
    /** Levar a conversa para fora do app. */
    private static final Pattern OFF_PLATFORM = startingWith("cmfaebpzwti",
            "\\b" + CONTACT_VERB + "\\b.{0,15}\\b(?:" + MESSENGER_WORD
                    + "|signal|insta|instagram|direct|dm|inbox|pv|privado)\\b"
                    + "|\\b" + MESSENGER_WORD + "\\b.{0,10}\\b(?:na bio|no perfil|no privado|no pv|na descricao)\\b"
                    + "|\\binteressados?\\s{0,3}(?:chama|chamar|chamem|inbox|pv|no privado|no pv|dm)\\b");
    private static final Pattern DELIVERY_OR_PAYMENT = startingWith("fedmpa",
            "\\b(?:faco entrega|fazemos entrega|entrego|entregamos|delivery|frete gratis|envio para todo|envio pra todo"
                    + "|faco envio|fazemos envio|mando pelo correio|mando pelos correios|entrego em maos|pago na entrega"
                    + "|aceito cartao|aceito transferencia|aceito " + PIX_WORD + "|aceitamos " + PIX_WORD
                    + "|" + PIX_WORD + " ou dinheiro)\\b");
    /** Pedido de PIX sem a chave escrita ("manda um pix", "chave pix na bio"). */
    private static final Pattern PIX_REQUEST = startingWith("mfrsacp",
            "\\b(?:manda|mande|faz|faca|faco|recebo|so|apenas|somente)\\s{1,3}(?:(?:um|o|no|via|por|de)\\s{1,3})?"
                    + PIX_WORD + "\\b"
                    + "|\\bchave\\s{1,3}(?:do\\s{1,3})?" + PIX_WORD + "\\b"
                    + "|\\b" + PIX_WORD + "\\s{1,3}(?:na bio|no perfil|na descricao|no privado|no pv)\\b");
    /** "@fulano" com contexto de negócio perto (menção comum a outro grower passa). */
    private static final Pattern HANDLE = Pattern.compile("(?<![a-z0-9_.@])@[a-z0-9_.]{2,30}");
    private static final Pattern BUSINESS_CONTEXT = startingWith("cmepoildzwt",
            "\\b(?:chama|chame|chamem|me chama|contato|contatos|encomendas?|pedidos?|orcamentos?|interessados?"
                    + "|lojinha|dm|direct|inbox|pv|privado|" + MESSENGER_WORD + ")\\b");
    private static final Pattern AD_PHRASE = startingWith("vpantfd",
            "\\b(?:valor a combinar|preco a combinar|aceito propostas?|aceito negociar|negociavel|tabela de precos"
                    + "|faco o (?:g|grama)|faco a (?:g|grama)|deixo por r?\\$?\\s?\\d)");
    private static final Pattern SELL_VERB = startingWith("vactr",
            "\\b(?:" + spaced("vendo") + "|" + spaced("vendemos") + "|vende-se|vendese|vende|vendendo|a venda"
                    + "|compro|compramos|troco|trocamos|repasso|repassamos)\\b");
    /** "troco a água", "troco as mudas de vaso": manutenção, não troca comercial. */
    private static final Pattern MAINTENANCE = startingWith("doa",
            "\\b(?:de vaso|o vaso|os vasos|de lugar|a agua|o substrato|a terra|a solucao|a lampada|as lampadas|o led"
                    + "|o filtro|de fase|a luz|o fotoperiodo|de posicao|o timer|o reservatorio|a rega)\\b");
    /** "estou vendo as folhas", "tô só vendo", "vendo que a EC subiu": gerúndio de ver, não venda. */
    private static final Pattern SEEING = startingWith("eftascvijnqp",
            "\\b(?:estou|to|ta|esta|estamos|estao|tao|tamo|tamos|fico|fica|ficou|fiquei|ficar|tava|tavam|estava|estavam"
                    + "|ando|anda|andei|sigo|continuo|continua|vou|vai|vamos|ir|indo|fui|foi|so|ja|nao|ainda|quando"
                    + "|enquanto|pra|para)\\s{1,3}(?:(?:so|ja|nao|ainda|sempre|agora|aqui|mesmo)\\s{1,3}){0,2}vendo\\b"
                    + "|\\bvendo\\s{1,3}(?:que|se|como|onde|quando)\\b");
    private static final Pattern PRODUCT = startingWith("gkphdirscembf",
            "\\b(?:gramas?|grs?|kg|prensado|haxixe|hash|kief|dry|ice|rosin|semente|sementes|clone|clones|estaca|estacas"
                    + "|muda|mudas|beck|becks|baseado|erva|flor|flores|buds?)\\b");
    private static final Pattern PRICE = startingWith("r\\s$0-9",
            "r?\\s?\\$\\s?\\d+|\\b\\d+(?:[.,]\\d+)?\\s?(?:reais|real|conto|contos|pila|pilas|mangos?)\\b");
    private static final Pattern QUANTITY = Pattern.compile("\\b\\d+(?:[.,]\\d+)?\\s?(?:g|gr|grs|gramas?|kg)\\b");
    /** "R$ 30 o grama", "50 reais cada muda". */
    private static final Pattern PER_UNIT = Pattern.compile(
            "\\b(?:o|a|por|cada)\\s{1,2}(?:grama|g|gr|kg|muda|clone|semente|unidade|un)\\b"
                    + "|/\\s?(?:g|gr|grama|kg|muda|un)\\b");
    /** Relato de gasto ("gastei R$ 300 no LED"): não é oferta. */
    private static final Pattern SPENT = startingWith("gpcise",
            "\\b(?:gastei|gastamos|paguei|pagamos|custou|custaram|investi|investimos|saiu|sairam|comprei|compramos"
                    + "|gasto|gastos|custo|custos|economizei)\\b");

    private static final int PIX_WINDOW = 60;
    private static final int MESSENGER_WINDOW = 30;
    private static final int PRICE_WINDOW = 40;
    private static final int HANDLE_WINDOW = 30;

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
        Views views = Views.of(joined.toString());
        return block(views).or(() -> hold(views)).orElse(Verdict.ALLOWED);
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

    // ------------------------------------------------------------------------------------- decisão

    private static Optional<Verdict> block(Views v) {
        if (v.mayHaveLink && MESSENGER_LINK.matcher(v.link()).find()) {
            return verdict(Decision.BLOCK, REASON_MESSENGER_LINK);
        }
        if (v.hasDigit && (CPF.matcher(v.digits).find() || CNPJ.matcher(v.digits).find())) {
            return verdict(Decision.BLOCK, REASON_PHONE);
        }
        for (Run run : v.runs) {
            if (run.phone() || run.document()) {
                return verdict(Decision.BLOCK, REASON_PHONE);
            }
        }
        if (anyNear(PIX.matcher(v.norm), v.keys(), PIX_WINDOW)
                || anyNear(KEY_WORD.matcher(v.norm), v.keys(), PIX_WINDOW)) {
            return verdict(Decision.BLOCK, REASON_PIX);
        }
        if (!v.localPhones().empty() && v.messengers().anyNear(v.localPhones(), MESSENGER_WINDOW)) {
            return verdict(Decision.BLOCK, REASON_PHONE);
        }
        return Optional.empty();
    }

    private static Optional<Verdict> hold(Views v) {
        String text = v.norm;
        if (OFF_PLATFORM.matcher(text).find()
                || !v.numbers().empty() && v.messengers().anyNear(v.numbers(), MESSENGER_WINDOW)) {
            return verdict(Decision.HOLD, REASON_OFF_PLATFORM);
        }
        for (Run run : v.runs) {
            if (run.maybePhone()) {
                return verdict(Decision.HOLD, REASON_MAYBE_PHONE);
            }
        }
        if (anyNear(PICS.matcher(text), v.keys(), PIX_WINDOW)) {
            return verdict(Decision.HOLD, REASON_MAYBE_PIX);
        }
        if (v.mayHaveLink && SHORT_LINK.matcher(v.link()).find()) {
            return verdict(Decision.HOLD, REASON_SHORT_LINK);
        }
        if (DELIVERY_OR_PAYMENT.matcher(text).find() || PIX_REQUEST.matcher(text).find()) {
            return verdict(Decision.HOLD, REASON_PAYMENT);
        }
        if (v.hasAt && EMAIL.matcher(v.plain).find() || v.maySpellEmail && SPELLED_EMAIL.matcher(v.plain).find()) {
            return verdict(Decision.HOLD, REASON_EMAIL);
        }
        if (v.hasAt) {
            Matcher handle = HANDLE.matcher(v.plain);
            if (handle.find() && anyNear(handle.reset(), Marks.of(BUSINESS_CONTEXT.matcher(text), text.length()),
                    HANDLE_WINDOW)) {
                return verdict(Decision.HOLD, REASON_OFF_PLATFORM);
            }
        }
        if (AD_PHRASE.matcher(text).find()) {
            return verdict(Decision.HOLD, REASON_AD);
        }
        boolean price = v.hasDigit && PRICE.matcher(text).find();
        if (sells(text) && (price || v.hasDigit && QUANTITY.matcher(text).find() || PRODUCT.matcher(text).find())) {
            return verdict(Decision.HOLD, REASON_AD);
        }
        if (price && pricedByQuantity(text)) {
            return verdict(Decision.HOLD, REASON_PRICE);
        }
        return Optional.empty();
    }

    /** Verbo de venda/troca, ignorando o gerúndio de ver e a troca de manutenção ("troco a água"). */
    private static boolean sells(String text) {
        if (!SELL_VERB.matcher(text).find()) {
            return false;
        }
        String withoutSeeing = SEEING.matcher(text).replaceAll(" ");
        Matcher sell = SELL_VERB.matcher(withoutSeeing);
        Matcher maintenance = MAINTENANCE.matcher(withoutSeeing).useTransparentBounds(true);
        while (sell.find()) {
            String word = sell.group();
            boolean trade = word.equals("troco") || word.equals("trocamos");
            if (!trade || !maintenance.region(sell.end(), Math.min(withoutSeeing.length(), sell.end() + 30)).find()) {
                return true;
            }
        }
        return false;
    }

    /** Preço colado em quantidade ("10g por 250 reais", "R$ 30 o grama"), fora de relato de gasto. */
    private static boolean pricedByQuantity(String text) {
        Marks units = null;
        Matcher price = PRICE.matcher(text);
        Matcher spent = SPENT.matcher(text).useTransparentBounds(true);
        while (price.find()) {
            if (spent.region(Math.max(0, price.start() - 25), price.start()).find()) {
                continue;
            }
            if (units == null) {
                units = Marks.of(QUANTITY.matcher(text), text.length()).and(PER_UNIT.matcher(text));
            }
            if (units.any(price.start() - PRICE_WINDOW, price.end() + PRICE_WINDOW)) {
                return true;
            }
        }
        return false;
    }

    /** Algum achado de {@code matcher} tem uma marca a até {@code radius} caracteres? */
    private static boolean anyNear(Matcher matcher, Marks marks, int radius) {
        if (marks.empty()) {
            return false;
        }
        while (matcher.find()) {
            if (marks.any(matcher.start() - radius, matcher.end() + radius)) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyNear(Marks spans, Marks marks, int radius) {
        return !marks.empty() && spans.anyNear(marks, radius);
    }

    private static Optional<Verdict> verdict(Decision decision, String reason) {
        return Optional.of(new Verdict(decision, reason));
    }

    // ------------------------------------------------------------------------------------- textos derivados

    /**
     * Versões do mesmo texto. {@code plain}, {@code norm} e {@code digits} têm o mesmo tamanho (trocas de um
     * caractere por outro), então uma posição achada numa vale nas outras. O que é caro só é calculado se for usado.
     * <ul>
     *   <li>{@code plain}: minúsculo, sem acento, sem caractere invisível e sem letra de outro alfabeto que imita
     *       as nossas;</li>
     *   <li>{@code norm}: {@code plain} com leetspeak desfeito ("v3nd0" vira "vendo");</li>
     *   <li>{@code digits}: {@code plain} com letras no lugar de dígitos desfeitas dentro de números ("432l" vira
     *       "4321") e sem UUIDs;</li>
     *   <li>{@code link}: {@code plain} sem espaço em volta de ponto e barra ("wa . me / 55" vira "wa.me/55").</li>
     * </ul>
     */
    private static final class Views {

        final String plain;
        final String norm;
        final String digits;
        final List<Run> runs;
        // Atalhos baratos: sem dígito não há telefone nem preço; sem "/" não há link de mensageiro; etc.
        final boolean hasDigit;
        final boolean hasAt;
        final boolean maySpellEmail;
        final boolean mayHaveLink;
        private String link;
        private Marks keys;
        private Marks messengers;
        private Marks localPhones;
        private Marks numbers;

        private Views(String raw) {
            plain = CommerceFilter.plain(raw);
            norm = unleetWords(plain);
            String lookalikes = digitsView(plain);
            hasDigit = containsDigit(lookalikes);
            hasAt = plain.indexOf('@') >= 0;
            maySpellEmail = hasAt || plain.contains("arroba")
                    || plain.contains("at") && (plain.contains("ponto") || plain.contains("dot") || plain.contains("."));
            mayHaveLink = plain.indexOf('/') >= 0 || plain.contains("whatsapp");
            digits = hasDigit ? withoutRandomKeys(lookalikes) : lookalikes;
            runs = hasDigit ? Run.all(digits) : List.of();
        }

        private static boolean containsDigit(String text) {
            for (int i = 0; i < text.length(); i++) {
                if (isDigit(text.charAt(i))) {
                    return true;
                }
            }
            return false;
        }

        static Views of(String raw) {
            return new Views(raw);
        }

        String link() {
            if (link == null) {
                link = linkView(plain);
            }
            return link;
        }

        /** Onde há algo que serve de chave PIX: e-mail, chave aleatória ou telefone (com ou sem DDD). */
        Marks keys() {
            if (keys == null) {
                keys = new Marks(plain.length()).and(localPhones());
                if (hasDigit) {
                    keys.and(RANDOM_KEY.matcher(plain));
                }
                if (hasAt) {
                    keys.and(EMAIL.matcher(plain));
                }
                if (maySpellEmail) {
                    keys.and(SPELLED_EMAIL.matcher(plain));
                }
            }
            return keys;
        }

        Marks messengers() {
            if (messengers == null) {
                messengers = Marks.of(MESSENGER.matcher(norm), norm.length());
            }
            return messengers;
        }

        Marks localPhones() {
            if (localPhones == null) {
                localPhones = runMarks(true);
            }
            return localPhones;
        }

        /** Números com 4 dígitos ou mais que não são um ano ("zap final 4321"). */
        Marks numbers() {
            if (numbers == null) {
                numbers = runMarks(false);
            }
            return numbers;
        }

        private Marks runMarks(boolean localPhone) {
            Marks marks = new Marks(plain.length());
            for (Run run : runs) {
                if (localPhone ? run.localPhone() : run.digitCount() >= 4 && !run.year()) {
                    marks.mark(run.start(), run.end());
                }
            }
            return marks;
        }
    }

    /**
     * Trechos marcados de um texto, com soma acumulada: "tem marca entre i e j?" custa O(1). Evita varrer de novo a
     * vizinhança de cada achado (o que ficaria quadrático num texto com milhares de "pix").
     */
    private static final class Marks {

        private final int[] starts;
        private final int[] ends;
        private int[] prefix;
        private int count;

        Marks(int length) {
            starts = new int[length + 1];
            ends = new int[length + 1];
        }

        static Marks of(Matcher matcher, int length) {
            return new Marks(length).and(matcher);
        }

        Marks and(Matcher matcher) {
            while (matcher.find()) {
                mark(matcher.start(), matcher.end());
            }
            return this;
        }

        Marks and(Marks other) {
            for (int i = 0; i < other.starts.length; i++) {
                starts[i] += other.starts[i];
                ends[i] += other.ends[i];
            }
            count += other.count;
            prefix = null;
            return this;
        }

        void mark(int start, int end) {
            starts[start]++;
            ends[end]++;
            count++;
            prefix = null;
        }

        boolean empty() {
            return count == 0;
        }

        /** Algum trecho marcado cruza [from, to)? */
        boolean any(int from, int to) {
            if (count == 0) {
                return false;
            }
            int[] covered = prefix();
            int lo = Math.max(0, from);
            int hi = Math.min(starts.length - 1, to);
            return lo < hi && covered[hi] - covered[lo] > 0;
        }

        /**
         * Algum trecho daqui está a até {@code radius} caracteres de um trecho de {@code other}? Olha em volta do
         * início e do fim de cada trecho, o que basta para trechos curtos (nomes de mensageiro).
         */
        boolean anyNear(Marks other, int radius) {
            for (int i = 0; i < starts.length; i++) {
                if (starts[i] > 0 && other.any(i - radius, i + radius + 1)
                        || ends[i] > 0 && other.any(i - radius, i + radius)) {
                    return true;
                }
            }
            return false;
        }

        /** covered[i] = quantos caracteres antes de i estão dentro de algum trecho. */
        private int[] prefix() {
            if (prefix == null) {
                prefix = new int[starts.length];
                int open = 0;
                for (int i = 0; i < starts.length - 1; i++) {
                    open += starts[i] - ends[i];
                    prefix[i + 1] = prefix[i] + (open > 0 ? 1 : 0);
                }
            }
            return prefix;
        }
    }

    /**
     * Sequência de dígitos com separadores. Os grupos ("11", "9", "8765", "4321") são combinados em janelas
     * contíguas para achar telefone mesmo com espaçamento estranho ou com número extra colado.
     */
    private record Run(int start, int end, int digitCount, boolean year, boolean phone, boolean maybePhone,
                       boolean localPhone, boolean document) {

        private static final int MAX_DIGITS = 14;

        static List<Run> all(String digitsView) {
            List<Run> runs = new ArrayList<>();
            Matcher m = DIGIT_RUN.matcher(digitsView);
            while (m.find()) {
                runs.add(of(m.group(), m.start(), m.end()));
            }
            return runs;
        }

        static Run of(String text, int start, int end) {
            List<String> groups = new ArrayList<>();
            StringBuilder separators = new StringBuilder();
            int i = 0;
            while (i < text.length()) {
                int j = i;
                while (j < text.length() && isDigit(text.charAt(j))) {
                    j++;
                }
                groups.add(text.substring(i, j));
                i = j;
                if (i < text.length()) {
                    separators.append(text.charAt(i));
                }
                while (i < text.length() && !isDigit(text.charAt(i))) {
                    i++;
                }
            }
            int digitCount = 0;
            for (String group : groups) {
                digitCount += group.length();
            }
            // Datas ("01/10/2026", "2026-10-01") não são telefone nem chave.
            boolean dateLike = text.indexOf('/') >= 0 || ISO_DATE.matcher(text).matches();
            boolean year = groups.size() == 1 && text.length() == 4 && (text.startsWith("19") || text.startsWith("20"));
            boolean phone = false;
            boolean maybePhone = false;
            boolean localPhone = false;
            boolean document = false;
            StringBuilder window = new StringBuilder(MAX_DIGITS + 8);
            for (int first = 0; first < groups.size(); first++) {
                window.setLength(0);
                int longest = 0;
                boolean single = true;
                boolean sameSeparator = true;
                for (int last = first; last < groups.size(); last++) {
                    String group = groups.get(last);
                    window.append(group);
                    if (window.length() > MAX_DIGITS) {
                        break;
                    }
                    longest = Math.max(longest, group.length());
                    single &= group.length() == 1;
                    if (last > first) {
                        sameSeparator &= separators.charAt(last - 1) == separators.charAt(first);
                    }
                    int count = last - first + 1;
                    int length = window.length();
                    if (length < 8) {
                        continue;
                    }
                    // "1 1 9 8 7 6 5 4 3 2 1" (dígito a dígito, mesmo separador) é disfarce; "5.8 6.0 6.2" é medida.
                    boolean spelledOut = single && sameSeparator && count > 1;
                    if (isPhone(window)) {
                        if (count <= 4 || spelledOut) {
                            phone = true;
                        } else if (count <= 6 && longest >= 3) {
                            maybePhone = true;
                        }
                    }
                    if ((count <= 5 || spelledOut) && (isCpf(window) || isCnpj(window))) {
                        document = true;
                    }
                    if (!dateLike && count <= 3 && isLocalPhone(window, 0)) {
                        localPhone = true;
                        maybePhone |= length == 9;
                    }
                }
            }
            return new Run(start, end, digitCount, year, phone, maybePhone, localPhone || phone, document);
        }

        /** Telefone brasileiro, com ou sem +55 / 0 na frente: DDD sem zero + celular com 9 ou fixo de 8 dígitos. */
        private static boolean isPhone(CharSequence d) {
            int n = d.length();
            if (n < 10 || n > 13) {
                return false;
            }
            return withAreaCode(d, 0)
                    || n >= 12 && d.charAt(0) == '5' && d.charAt(1) == '5' && withAreaCode(d, 2)
                    || d.charAt(0) == '0' && withAreaCode(d, 1);
        }

        private static boolean withAreaCode(CharSequence d, int from) {
            return d.length() - from >= 10 && d.charAt(from) != '0' && d.charAt(from + 1) != '0'
                    && isLocalPhone(d, from + 2);
        }

        /** Sem DDD: celular "9xxxx-xxxx" ou fixo "xxxx-xxxx" começando de 2 a 9. */
        private static boolean isLocalPhone(CharSequence d, int from) {
            int n = d.length() - from;
            char first = d.charAt(from);
            return n == 9 && first == '9' || n == 8 && first >= '2';
        }

        private static boolean isCpf(CharSequence d) {
            if (d.length() != 11 || repeated(d)) {
                return false;
            }
            return checkDigit(d, 9, 10) == d.charAt(9) - '0' && checkDigit(d, 10, 11) == d.charAt(10) - '0';
        }

        private static int checkDigit(CharSequence d, int length, int firstWeight) {
            int sum = 0;
            for (int i = 0; i < length; i++) {
                sum += (d.charAt(i) - '0') * (firstWeight - i);
            }
            int rest = sum % 11;
            return rest < 2 ? 0 : 11 - rest;
        }

        private static boolean isCnpj(CharSequence d) {
            if (d.length() != 14 || repeated(d)) {
                return false;
            }
            return cnpjDigit(d, 12) == d.charAt(12) - '0' && cnpjDigit(d, 13) == d.charAt(13) - '0';
        }

        private static int cnpjDigit(CharSequence d, int length) {
            int sum = 0;
            int weight = length - 7;
            for (int i = 0; i < length; i++) {
                sum += (d.charAt(i) - '0') * weight;
                weight = weight == 2 ? 9 : weight - 1;
            }
            int rest = sum % 11;
            return rest < 2 ? 0 : 11 - rest;
        }

        private static boolean repeated(CharSequence d) {
            for (int i = 1; i < d.length(); i++) {
                if (d.charAt(i) != d.charAt(0)) {
                    return false;
                }
            }
            return true;
        }
    }

    // ------------------------------------------------------------------------------------- normalização

    private static final Pattern MARKS = Pattern.compile("[\\p{M}\\p{Cf}]");
    private static final Pattern LINK_DOT = Pattern.compile(
            "\\s{0,3}[(\\[{]\\s{0,2}(?:\\.|dot|ponto)\\s{0,2}[)\\]}]\\s{0,3}|\\s{1,3}(?:ponto|dot)\\s{1,3}");
    private static final Pattern LINK_SPACE = Pattern.compile("\\s{1,3}(?=[./:])|(?<=[./:])\\s{1,3}");

    /**
     * Sem acento e com as trocas de letra por número mais comuns ("v3nd0" vira "vendo"). Só troca dentro de
     * palavras com pelo menos duas letras, para números de verdade ("5g", "200", "1.4") ficarem como estão.
     */
    static String normalize(String text) {
        return unleetWords(plain(text));
    }

    /** Minúsculo, sem acento, sem caractere invisível; letras gregas/cirílicas parecidas viram as latinas. */
    private static String plain(String text) {
        String lower = homoglyphs(text.toLowerCase(Locale.ROOT));
        return MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFKD)).replaceAll("");
    }

    private static String homoglyphs(String text) {
        char[] out = text.toCharArray();
        for (int i = 0; i < out.length; i++) {
            out[i] = switch (out[i]) {
                case 'а', 'α' -> 'a';
                case 'е', 'ε' -> 'e';
                case 'о', 'ο' -> 'o';
                case 'р', 'ρ' -> 'p';
                case 'с' -> 'c';
                case 'х', 'χ' -> 'x';
                case 'у' -> 'y';
                case 'і', 'ι' -> 'i';
                case 'к', 'κ' -> 'k';
                case 'т', 'τ' -> 't';
                case 'ѕ' -> 's';
                case 'ԁ' -> 'd';
                case 'ν' -> 'v';
                case 'ո' -> 'n';
                default -> out[i];
            };
        }
        return new String(out);
    }

    /** Palavra para o leetspeak: letras, dígitos e os símbolos que imitam letra ("v3nd@", "p!x", "ve$$"). */
    private static boolean isLeetChar(char c) {
        return c >= 'a' && c <= 'z' || isDigit(c) || c == '@' || c == '$' || c == '!' || c == '|';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isAlnum(char c) {
        return c >= 'a' && c <= 'z' || isDigit(c);
    }

    private static String unleetWords(String plain) {
        char[] out = plain.toCharArray();
        int i = 0;
        while (i < out.length) {
            if (!isLeetChar(plain.charAt(i))) {
                i++;
                continue;
            }
            int end = i;
            int letters = 0;
            while (end < out.length && isLeetChar(plain.charAt(end))) {
                letters += plain.charAt(end) >= 'a' && plain.charAt(end) <= 'z' ? 1 : 0;
                end++;
            }
            if (letters >= 2) {
                for (int k = i; k < end; k++) {
                    // "!", "|" e "$" só entre letras/dígitos ("p!x", "ve$$o"): no fim ("vendo!") são pontuação.
                    boolean inside = k > i && k < end - 1 && isAlnum(plain.charAt(k - 1)) && isAlnum(plain.charAt(k + 1));
                    out[k] = switch (out[k]) {
                        case '0' -> 'o';
                        case '1' -> 'i';
                        case '3' -> 'e';
                        case '4' -> 'a';
                        case '5' -> 's';
                        case '7' -> 't';
                        case '@' -> 'a';
                        case '!', '|' -> inside ? 'i' : out[k];
                        case '$' -> inside ? 's' : out[k];
                        default -> out[k];
                    };
                }
            }
            i = end;
        }
        return new String(out);
    }

    /** Dentro de números, "o" vira 0 e "l"/"i" viram 1 ("98765-432l", "2oo"). Palavras ficam como estão. */
    private static String digitsView(String plain) {
        char[] out = plain.toCharArray();
        int i = 0;
        while (i < out.length) {
            if (!isAlnum(out[i])) {
                i++;
                continue;
            }
            int end = i;
            int digits = 0;
            boolean onlyLookalikes = true;
            while (end < out.length && isAlnum(out[end])) {
                char c = out[end];
                if (isDigit(c)) {
                    digits++;
                } else {
                    onlyLookalikes &= c == 'o' || c == 'l' || c == 'i';
                }
                end++;
            }
            if (digits > 0 && digits < end - i && onlyLookalikes && end - i >= 3) {
                for (int k = i; k < end; k++) {
                    out[k] = switch (out[k]) {
                        case 'o' -> '0';
                        case 'l', 'i' -> '1';
                        default -> out[k];
                    };
                }
            }
            i = end;
        }
        return new String(out);
    }

    /** UUID (link de diário, chave aleatória) não é telefone: some da versão de dígitos. */
    private static String withoutRandomKeys(String digits) {
        Matcher keys = RANDOM_KEY.matcher(digits);
        if (!keys.find()) {
            return digits;
        }
        char[] out = digits.toCharArray();
        do {
            for (int i = keys.start(); i < keys.end(); i++) {
                out[i] = ' ';
            }
        } while (keys.find());
        return new String(out);
    }

    private static String linkView(String plain) {
        String dotted = LINK_DOT.matcher(plain).replaceAll(".");
        return LINK_SPACE.matcher(dotted).replaceAll("");
    }

    /**
     * Só tenta casar onde o texto começa com um destes caracteres. Todas as alternativas de {@code regex} precisam
     * começar com um deles; o atalho evita testar dezenas de alternativas em cada posição de um texto longo.
     */
    private static Pattern startingWith(String firstChars, String regex) {
        return Pattern.compile("(?=[" + firstChars + "])(?:" + regex + ")");
    }

    /** Padrão que aceita até três separadores entre as letras e letras repetidas ("p.i.x", "z a p", "piiix"). */
    private static String spaced(String word) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            if (i > 0) {
                out.append(SEP);
            }
            out.append(word.charAt(i)).append('+');
        }
        return out.toString();
    }
}
