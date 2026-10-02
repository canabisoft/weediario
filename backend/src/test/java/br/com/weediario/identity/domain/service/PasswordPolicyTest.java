package br.com.weediario.identity.domain.service;

import br.com.weediario.identity.domain.model.Username;
import br.com.weediario.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private static final Username USER = new Username("grower_teste");

    @ParameterizedTest
    @ValueSource(strings = {"varanda com tres vasos", "Ab3$Ab3$Ab3$", "minha tenda de 60 por 60", "çãõéü ação maçã"})
    void acceptsLongPasswordsWithoutComplexityRules(String password) {
        assertThat(PasswordPolicy.requireAcceptable(password, USER)).isEqualTo(password);
    }

    @ParameterizedTest
    @ValueSource(strings = {"curta", "123456789", "1234567890", "qwertyuiop", "Senha12345", "PASSWORD123",
            "aaaaaaaaaaaa", "ababababababab", "GROWER_TESTE123"})
    void refusesShortCommonRepetitiveOrUsernamePasswords(String password) {
        assertThatThrownBy(() -> PasswordPolicy.requireAcceptable(password, USER))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void refusesBlankAndHugePasswords() {
        assertThatThrownBy(() -> PasswordPolicy.requireAcceptable("   ", USER)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> PasswordPolicy.requireAcceptable(null, USER)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> PasswordPolicy.requireAcceptable("x1y2z3w4".repeat(17), USER))
                .isInstanceOf(DomainException.class);
    }
}
