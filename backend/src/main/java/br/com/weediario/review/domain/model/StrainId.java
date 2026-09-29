package br.com.weediario.review.domain.model;

import java.util.Objects;
import java.util.UUID;

public record StrainId(UUID value) {

    public StrainId {
        Objects.requireNonNull(value, "StrainId");
    }

    public static StrainId newId() {
        return new StrainId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
