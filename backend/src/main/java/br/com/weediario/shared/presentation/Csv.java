package br.com.weediario.shared.presentation;

/**
 * Células de CSV seguras para abrir em planilha.
 */
public final class Csv {

    private Csv() {
    }

    /** Aspas em volta, aspas internas dobradas e sem fórmula de planilha (=, +, -, @ no começo). */
    public static String cell(String value) {
        if (value == null) {
            return "";
        }
        String safe = value.replace("\r", " ").replace("\n", " ");
        if (!safe.isEmpty() && "=+-@\t".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    /** Valor não textual (número, enum, data); nulo vira célula vazia. */
    public static String value(Object value) {
        return value == null ? "" : cell(value.toString());
    }
}
