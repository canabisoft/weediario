package br.com.weediario.shared.domain;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Número escrito por extenso ("onze nove oito sete seis…") vira dígitos, para o filtro anti-comércio enxergar o
 * telefone ou o CPF. Só converte sequências longas (8 ou mais dígitos): "um dois" ou "vinte e cinco dias" ficam
 * como estão. Recebe texto já em minúsculas e sem acento.
 */
final class SpelledNumbers {

    private static final int MIN_DIGITS = 8;

    private static final Map<String, String> WORDS = Map.ofEntries(
            Map.entry("zero", "0"), Map.entry("um", "1"), Map.entry("uma", "1"), Map.entry("dois", "2"),
            Map.entry("duas", "2"), Map.entry("tres", "3"), Map.entry("quatro", "4"), Map.entry("cinco", "5"),
            Map.entry("seis", "6"), Map.entry("meia", "6"), Map.entry("sete", "7"), Map.entry("oito", "8"),
            Map.entry("nove", "9"), Map.entry("dez", "10"), Map.entry("onze", "11"), Map.entry("doze", "12"),
            Map.entry("treze", "13"), Map.entry("catorze", "14"), Map.entry("quatorze", "14"), Map.entry("quinze", "15"),
            Map.entry("dezesseis", "16"), Map.entry("dezessete", "17"), Map.entry("dezoito", "18"),
            Map.entry("dezenove", "19"));
    private static final Map<String, String> TENS = Map.of(
            "vinte", "2", "trinta", "3", "quarenta", "4", "cinquenta", "5", "sessenta", "6", "setenta", "7",
            "oitenta", "8", "noventa", "9");

    private static final String WORD = "(?:" + String.join("|", WORDS.keySet()) + "|" + String.join("|", TENS.keySet()) + ")";
    private static final Pattern TOKEN = Pattern.compile(WORD);
    private static final Pattern WHOLE_WORD = Pattern.compile("\\b" + WORD + "\\b");
    private static final int MIN_WORDS = 4;

    private SpelledNumbers() {
    }

    /**
     * Varredura linear palavra a palavra. Uma regex única para a sequência inteira (palavra, separador, palavra...)
     * recursa a cada repetição no motor do Java e estoura a pilha com um texto longo de números por extenso: virava um
     * jeito de derrubar a requisição com um comentário.
     */
    static String toDigits(String text) {
        if (text.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        Matcher word = WHOLE_WORD.matcher(text);
        int copied = 0;
        int runStart = -1;
        int runEnd = -1;
        int words = 0;
        while (word.find()) {
            boolean continues = runStart >= 0 && isSeparator(text.substring(runEnd, word.start()));
            if (!continues) {
                copied = flush(text, out, copied, runStart, runEnd, words);
                runStart = word.start();
                words = 0;
            }
            runEnd = word.end();
            words++;
        }
        copied = flush(text, out, copied, runStart, runEnd, words);
        return out.append(text, copied, text.length()).toString();
    }

    /**
     * O que separa duas palavras-número da mesma sequência: espaço; vírgula, ponto e vírgula ou hífen seguido de
     * espaço; ou "e" entre espaços. Conferido sem regex, em tempo linear mesmo num vão enorme.
     */
    private static boolean isSeparator(String gap) {
        String core = gap.strip();
        if (gap.isEmpty() || !Character.isWhitespace(gap.charAt(gap.length() - 1))) {
            return false;
        }
        if (core.isEmpty()) {
            return true;
        }
        if (core.equals("e")) {
            return Character.isWhitespace(gap.charAt(0));
        }
        return core.length() == 1 && ",;-".indexOf(core.charAt(0)) >= 0;
    }

    /** Fecha a sequência [start, end): troca por dígitos se for longa o bastante. Devolve até onde o texto foi copiado. */
    private static int flush(String text, StringBuilder out, int copied, int start, int end, int words) {
        if (start < 0 || words < MIN_WORDS) {
            return copied;
        }
        String run = text.substring(start, end);
        String digits = digitsOf(run);
        out.append(text, copied, start).append(digits.length() >= MIN_DIGITS ? digits : run);
        return end;
    }

    /** "vinte e um" vira "21"; "vinte" sozinho vira "20"; os demais, o próprio valor. */
    private static String digitsOf(String run) {
        StringBuilder digits = new StringBuilder();
        Matcher token = TOKEN.matcher(run);
        String pendingTen = null;
        while (token.find()) {
            String word = token.group();
            if (TENS.containsKey(word)) {
                if (pendingTen != null) {
                    digits.append(pendingTen).append('0');
                }
                pendingTen = TENS.get(word);
                continue;
            }
            String value = WORDS.get(word);
            if (pendingTen != null && value.length() == 1) {
                digits.append(pendingTen).append(value);
            } else {
                if (pendingTen != null) {
                    digits.append(pendingTen).append('0');
                }
                digits.append(value);
            }
            pendingTen = null;
        }
        if (pendingTen != null) {
            digits.append(pendingTen).append('0');
        }
        return digits.toString();
    }
}
