package br.com.weediario.media.domain.model;

/**
 * Formato em que a plataforma gravou a variante. Nunca é o formato enviado pelo cliente: toda imagem é re-codificada.
 * <p>
 * Imagens antigas foram gravadas em JPEG; as novas saem em WebP (JPEG só se o encoder WebP não estiver disponível).
 */
public enum ImageFormat {
    WEBP("image/webp", "webp"),
    JPEG("image/jpeg", "jpg");

    private final String mediaType;
    private final String extension;

    ImageFormat(String mediaType, String extension) {
        this.mediaType = mediaType;
        this.extension = extension;
    }

    public String mediaType() {
        return mediaType;
    }

    public String extension() {
        return extension;
    }
}
