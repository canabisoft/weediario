package br.com.weediario.media.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ImageId(UUID value) {

    public ImageId {
        Objects.requireNonNull(value, "ImageId");
    }

    public static ImageId newId() {
        return new ImageId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
