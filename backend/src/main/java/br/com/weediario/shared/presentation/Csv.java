package br.com.weediario.shared.presentation;

/**
 * Células de CSV seguras para abrir em planilha.
 */
public final class Csv {

    /**
     * Começos que uma planilha pode tratar como fórmula ou comando (OWASP CSV Injection), incluindo as versões de
     * largura total de = + - @, que algumas planilhas convertem.
     */
    private static final String FORMULA_START = "=+-@\t\r|%＝＋－＠";

    private Csv() {
    }

    /**
     * Aspas em volta, aspas internas dobradas, sem quebra de linha e sem fórmula de planilha: se o primeiro caractere
     * visível (depois de espaços no começo) puder abrir uma fórmula, a célula ganha um apóstrofo na frente e vira texto.
     */
    public static String cell(String value) {
        if (value == null) {
            return "";
        }
        String safe = value.replace("\r", " ").replace("\n", " ");
        String visible = safe.stripLeading();
        if (!visible.isEmpty() && FORMULA_START.indexOf(visible.charAt(0)) >= 0
                || !safe.isEmpty() && FORMULA_START.indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    /** Valor não textual (número, enum, data); nulo vira célula vazia. */
    public static String value(Object value) {
        return value == null ? "" : cell(value.toString());
    }
}
