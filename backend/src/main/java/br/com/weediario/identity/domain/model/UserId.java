package br.com.weediario.identity.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * UUID v4 (aleatório): ao contrário do v7, não embute timestamp de criação da conta.
 */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "UserId");
    }

    public static UserId newId() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId of(String raw) {
        return new UserId(UUID.fromString(raw));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
