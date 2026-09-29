package br.com.weediario.media.application.port;

/**
 * Converte o arquivo enviado em imagens novas, geradas a partir dos pixels, sem nenhum metadado de origem
 * (EXIF, GPS, XMP, IPTC, perfil ICC, comentários e miniaturas embutidas).
 */
public interface ImageSanitizer {

    /**
     * @throws br.com.weediario.media.application.exception.InvalidImageException se o conteúdo não for uma imagem aceita
     */
    SanitizedImage sanitize(byte[] content);

    record SanitizedImage(byte[] full, int width, int height, byte[] thumbnail) {
    }
}
