package br.com.weediario.identity.domain.model;

import br.com.weediario.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailAddressTest {

    @Test
    void normalizesAndMasks() {
        EmailAddress email = new EmailAddress("  Bruno.Grower@Gmail.com ");

        assertThat(email.value()).isEqualTo("bruno.grower@gmail.com");
        assertThat(email.masked()).isEqualTo("br***@gm***.com");
        assertThat(email.toString()).doesNotContain("bruno.grower");
    }

    @ParameterizedTest
    @ValueSource(strings = {"sem-arroba", "a@b", "a@@b.com", "espaço @x.com", "@exemplo.com"})
    void refusesInvalid(String value) {
        assertThatThrownBy(() -> new EmailAddress(value)).isInstanceOf(DomainException.class);
    }
}
