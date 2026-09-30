package br.com.weediario.media.application;

import br.com.weediario.media.domain.model.ImageFormat;

import java.util.Objects;

/**
 * Bytes de uma variante já sanitizada, junto com o formato em que foram codificados (para o Content-Type).
 */
public record EncodedImage(byte[] content, ImageFormat format) {

    public EncodedImage {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(format, "format");
    }

    public String mediaType() {
        return format.mediaType();
    }
}
