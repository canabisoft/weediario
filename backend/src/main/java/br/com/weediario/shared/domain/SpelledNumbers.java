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
    /** Sequência de palavras-número, separadas por espaço, vírgula, hífen ou "e". */
    private static final Pattern RUN = Pattern.compile(
            "\\b" + WORD + "(?:(?:\\s*[,;\\-]?\\s+|\\s+e\\s+)" + WORD + "){3,}\\b");
    private static final Pattern TOKEN = Pattern.compile(WORD);

    private SpelledNumbers() {
    }

    static String toDigits(String text) {
        if (text.isEmpty()) {
            return text;
        }
        Matcher run = RUN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (run.find()) {
            String digits = digitsOf(run.group());
            run.appendReplacement(out, Matcher.quoteReplacement(digits.length() >= MIN_DIGITS ? digits : run.group()));
        }
        run.appendTail(out);
        return out.toString();
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
