package br.com.weediario.identity.presentation.rest;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TermsControllerTest {

    @Test
    void acceptsIsoAndBrazilianDatesWithOrWithoutQuotes() {
        LocalDate expected = LocalDate.of(2026, 11, 4);
        assertThat(TermsController.parse("2026-11-04")).isEqualTo(expected);
        assertThat(TermsController.parse("04/11/2026")).isEqualTo(expected);
        assertThat(TermsController.parse(" '04/11/2026' ")).isEqualTo(expected);
    }
}
