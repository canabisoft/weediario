package br.com.weediario.shared.presentation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CsvTest {

    /** Payloads de CSV injection da OWASP e variações com espaço no começo ou caractere de largura total. */
    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+1+1", "-1+1", "@SUM(A1)", "\t=1", "|calc", "%0A=1", "  =HYPERLINK(\"x\")",
            " @cmd", "＝1+1", "＋1", "－1", "＠SUM(A1)", "=cmd|' /C calc'!A0"})
    void formulasBecomePlainText(String payload) {
        String cell = Csv.cell(payload);
        assertThat(cell).startsWith("\"'");
    }

    @Test
    void ordinaryTextIsKeptQuotedWithoutLineBreaks() {
        assertThat(Csv.cell("Gorilla Glue #4")).isEqualTo("\"Gorilla Glue #4\"");
        assertThat(Csv.cell("diz \"oi\"\nlinha")).isEqualTo("\"diz \"\"oi\"\" linha\"");
        assertThat(Csv.cell(null)).isEmpty();
        assertThat(Csv.value(42)).isEqualTo("\"42\"");
    }
}
