package br.com.weediario.media.infrastructure.imaging;

import br.com.weediario.media.application.exception.InvalidImageException;
import br.com.weediario.media.application.port.ImageSanitizer;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

/**
 * Sanitização por re-codificação: a imagem é decodificada até os pixels e um JPEG novo é gerado do zero.
 * <p>
 * Isso é mais seguro do que remover tags uma a uma: qualquer metadado (EXIF, GPS, XMP, IPTC, ICC, comentários,
 * miniaturas embutidas, dados após o fim do arquivo) simplesmente não existe na saída, inclusive os que nenhuma
 * biblioteca conhece. O reader é aberto com {@code ignoreMetadata=true}, então os metadados nem são carregados.
 * <p>
 * Defesas contra imagens maliciosas: formato detectado por magic bytes, limite de megapixels checado antes de
 * decodificar (decompression bomb), subamostragem na leitura e limite de decodificações simultâneas.
 */
@Component
public class ImageIoSanitizer implements ImageSanitizer {

    static final int FULL_MAX_EDGE = 2048;
    static final int THUMBNAIL_MAX_EDGE = 480;
    static final long MAX_SOURCE_PIXELS = 50_000_000L;
    private static final float JPEG_QUALITY = 0.85f;
    private static final int MAX_CONCURRENT_DECODES = 2;

    private final Semaphore decodePermits = new Semaphore(MAX_CONCURRENT_DECODES, true);

    public ImageIoSanitizer() {
        // Registra os plugins TwelveMonkeys (JPEG CMYK, WebP) também quando rodando de dentro do fat jar.
        ImageIO.scanForPlugins();
        ImageIO.setUseCache(false);
    }

    @Override
    public SanitizedImage sanitize(byte[] content) {
        SourceFormat format = SourceFormat.detect(content)
                .orElseThrow(() -> new InvalidImageException("Formato não suportado. Envie JPEG, PNG ou WebP."));
        int orientation = ExifOrientation.read(content);

        BufferedImage upright = ExifOrientation.apply(decode(content, format), orientation);
        BufferedImage full = fitWithin(upright, FULL_MAX_EDGE);
        BufferedImage thumbnail = fitWithin(full, THUMBNAIL_MAX_EDGE);

        return new SanitizedImage(encodeJpeg(full), full.getWidth(), full.getHeight(), encodeJpeg(thumbnail));
    }

    private BufferedImage decode(byte[] content, SourceFormat format) {
        decodePermits.acquireUninterruptibly();
        ImageReader reader = null;
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            reader = readerFor(format, input);
            reader.setInput(input, true, true);

            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width <= 0 || height <= 0 || (long) width * height > MAX_SOURCE_PIXELS) {
                throw new InvalidImageException("Resolução da imagem acima do permitido");
            }

            ImageReadParam param = reader.getDefaultReadParam();
            int subsampling = Math.max(1, Math.max(width, height) / (FULL_MAX_EDGE * 2));
            param.setSourceSubsampling(subsampling, subsampling, 0, 0);

            return toOpaqueRgb(reader.read(0, param));
        } catch (InvalidImageException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new InvalidImageException("Imagem corrompida ou ilegível");
        } finally {
            if (reader != null) {
                reader.dispose();
            }
            decodePermits.release();
        }
    }

    private static ImageReader readerFor(SourceFormat format, ImageInputStream input) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format.imageIoName());
        if (input == null || !readers.hasNext()) {
            throw new InvalidImageException("Formato não suportado. Envie JPEG, PNG ou WebP.");
        }
        return readers.next();
    }

    /** Descarta canal alfa (fundo branco) e normaliza o modelo de cor para sRGB 8 bits. */
    private static BufferedImage toOpaqueRgb(BufferedImage source) {
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, target.getWidth(), target.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /** Redução em etapas de 50% + ajuste final bicúbico: qualidade próxima de um resize "de verdade", sem libs nativas. */
    private static BufferedImage fitWithin(BufferedImage source, int maxEdge) {
        int longEdge = Math.max(source.getWidth(), source.getHeight());
        if (longEdge <= maxEdge) {
            return source;
        }
        double scale = (double) maxEdge / longEdge;
        int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage current = source;
        while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return resize(current, targetWidth, targetHeight, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static BufferedImage resize(BufferedImage source, int width, int height, Object interpolation) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            // Metadados nulos: o writer gera apenas o cabeçalho JFIF mínimo, sem EXIF/XMP.
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
