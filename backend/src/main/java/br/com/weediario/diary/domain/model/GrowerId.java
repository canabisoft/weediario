package br.com.weediario.diary.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Referência ao autor do diário. O contexto de diários conhece o grower apenas pelo id,
 * sem depender do modelo {@code User} do contexto de identidade.
 */
public record GrowerId(UUID value) {

    public GrowerId {
        Objects.requireNonNull(value, "GrowerId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
