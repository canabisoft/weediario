package br.com.weediario.media.infrastructure.imaging;

import br.com.weediario.media.application.EncodedImage;
import br.com.weediario.media.domain.model.ImageFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Optional;

/**
 * Codifica pixels já sanitizados. Usa WebP (libwebp via JNI) e cai para JPEG se o encoder nativo não carregar
 * na plataforma (ex.: Alpine/musl), para que o upload não pare por causa disso.
 * <p>
 * Em ambos os casos os metadados passados ao writer são nulos: a saída não tem EXIF, XMP nem ICC.
 */
final class ImageEncoder {

    private static final Logger log = LoggerFactory.getLogger(ImageEncoder.class);

    private final ImageFormat format;

    private ImageEncoder(ImageFormat format) {
        this.format = format;
    }

    /** Testa o WebP uma vez, codificando 1px, porque a falha do JNI só aparece no primeiro uso. */
    static ImageEncoder detect() {
        if (webpWriter().isPresent()) {
            try {
                ImageEncoder webp = new ImageEncoder(ImageFormat.WEBP);
                webp.encode(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), 0.5f);
                return webp;
            } catch (RuntimeException | LinkageError e) {
                log.warn("Encoder WebP indisponível nesta plataforma ({}); imagens serão gravadas em JPEG", e.toString());
            }
        } else {
            log.warn("Nenhum ImageWriter WebP registrado; imagens serão gravadas em JPEG");
        }
        return new ImageEncoder(ImageFormat.JPEG);
    }

    ImageFormat format() {
        return format;
    }

    /** @param quality de 0 a 1, com significado aproximado equivalente para WebP e JPEG. */
    EncodedImage encode(BufferedImage image, float quality) {
        ImageWriter writer = switch (format) {
            case WEBP -> webpWriter().orElseThrow();
            case JPEG -> ImageIO.getImageWritersByFormatName("jpeg").next();
        };
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            if (format == ImageFormat.WEBP) {
                param.setCompressionType(lossyType(param));
            } else {
                param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            }
            param.setCompressionQuality(quality);
            // Metadados nulos: só o cabeçalho mínimo do formato (JFIF / RIFF-VP8), sem EXIF/XMP.
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return new EncodedImage(bytes.toByteArray(), format);
    }

    private static String lossyType(ImageWriteParam param) {
        for (String type : param.getCompressionTypes()) {
            if (type.equalsIgnoreCase("Lossy")) {
                return type;
            }
        }
        return param.getCompressionTypes()[0];
    }

    /** O TwelveMonkeys também registra um plugin "webp", mas só de leitura; aqui interessa o que escreve. */
    private static Optional<ImageWriter> webpWriter() {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
        return writers.hasNext() ? Optional.of(writers.next()) : Optional.empty();
    }
}
