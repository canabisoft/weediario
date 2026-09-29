package br.com.weediario.diary.domain.model;

import java.util.Objects;
import java.util.UUID;

public record GrowDiaryId(UUID value) {

    public GrowDiaryId {
        Objects.requireNonNull(value, "GrowDiaryId");
    }

    public static GrowDiaryId newId() {
        return new GrowDiaryId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
