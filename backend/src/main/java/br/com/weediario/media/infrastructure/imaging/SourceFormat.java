package br.com.weediario.media.infrastructure.imaging;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Detecção pelo conteúdo (magic bytes). Content-Type e extensão enviados pelo cliente são ignorados,
 * pois são controlados por ele.
 */
enum SourceFormat {
    JPEG("jpeg"),
    PNG("png"),
    WEBP("webp");

    private final String imageIoName;

    SourceFormat(String imageIoName) {
        this.imageIoName = imageIoName;
    }

    String imageIoName() {
        return imageIoName;
    }

    static Optional<SourceFormat> detect(byte[] content) {
        if (startsWith(content, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, 0, 'R', 'I', 'F', 'F') && startsWith(content, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    /** HEIC/HEIF (padrão da câmera do iPhone e de vários Android): não é decodificado, só reconhecido para orientar. */
    static boolean isHeif(byte[] content) {
        if (!startsWith(content, 4, 'f', 't', 'y', 'p') || content.length < 12) {
            return false;
        }
        String brand = new String(content, 8, 4, StandardCharsets.US_ASCII);
        return brand.startsWith("hei") || brand.startsWith("hev") || brand.equals("mif1") || brand.equals("msf1");
    }

    private static boolean startsWith(byte[] content, int offset, int... signature) {
        if (content.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
