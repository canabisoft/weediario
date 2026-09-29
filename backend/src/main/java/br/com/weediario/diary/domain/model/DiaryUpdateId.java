package br.com.weediario.diary.domain.model;

import java.util.Objects;
import java.util.UUID;

public record DiaryUpdateId(UUID value) {

    public DiaryUpdateId {
        Objects.requireNonNull(value, "DiaryUpdateId");
    }

    public static DiaryUpdateId newId() {
        return new DiaryUpdateId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
