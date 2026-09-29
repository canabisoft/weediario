package br.com.weediario.diary.domain.model;

import java.util.Objects;
import java.util.UUID;

public record GrowSetupId(UUID value) {

    public GrowSetupId {
        Objects.requireNonNull(value, "GrowSetupId");
    }

    public static GrowSetupId newId() {
        return new GrowSetupId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
