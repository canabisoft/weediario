package br.com.weediario.identity.domain.model;

import br.com.weediario.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessKeyTest {

    @Test
    void generatesPrefixedHighEntropyKeys() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            AccessKey key = AccessKey.generate();
            assertThat(key.value()).startsWith(AccessKey.PREFIX).hasSize(AccessKey.LENGTH);
            keys.add(key.value());
        }
        assertThat(keys).hasSize(200);
    }

    @Test
    void storesOnlyADeterministicHashThatComparesInConstantTime() {
        AccessKey key = AccessKey.generate();
        AccessKeyHash hash = key.hash();

        assertThat(hash.value()).hasSize(64).doesNotContain(key.value().substring(AccessKey.PREFIX.length()));
        assertThat(new AccessKey(" " + key.value() + " ").hash().matches(hash)).isTrue();
        assertThat(AccessKey.generate().hash().matches(hash)).isFalse();
        assertThat(hash.matches(null)).isFalse();
    }

    @Test
    void rejectsMalformedKeysAndNeverPrintsTheKey() {
        assertThat(AccessKey.isWellFormed("senha-bem-longa-123")).isFalse();
        assertThat(AccessKey.isWellFormed("wd_sec_curta")).isFalse();
        assertThatThrownBy(() -> new AccessKey("wd_sec_curta")).isInstanceOf(DomainException.class);

        AccessKey key = AccessKey.generate();
        assertThat(key.toString()).doesNotContain(key.value());
        assertThat(key.hash().toString()).doesNotContain(key.hash().value());
    }
}
