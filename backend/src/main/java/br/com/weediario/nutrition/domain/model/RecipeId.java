package br.com.weediario.nutrition.domain.model;

import java.util.Objects;
import java.util.UUID;

public record RecipeId(UUID value) {

    public RecipeId {
        Objects.requireNonNull(value, "RecipeId");
    }

    public static RecipeId newId() {
        return new RecipeId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
