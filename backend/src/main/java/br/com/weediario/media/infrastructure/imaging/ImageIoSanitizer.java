package br.com.weediario.media.infrastructure.imaging;

import br.com.weediario.media.application.exception.InvalidImageException;
import br.com.weediario.media.application.port.ImageSanitizer;
import br.com.weediario.media.domain.model.ImageFormat;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

/**
 * Sanitização por re-codificação: a imagem é decodificada até os pixels e arquivos novos são gerados do zero.
 * <p>
 * Isso é mais seguro do que remover tags uma a uma: qualquer metadado (EXIF, GPS, XMP, IPTC, ICC, comentários,
 * miniaturas embutidas, dados após o fim do arquivo) simplesmente não existe na saída, inclusive os que nenhuma
 * biblioteca conhece. O reader é aberto com {@code ignoreMetadata=true}, então os metadados nem são carregados.
 * <p>
 * Saem duas variantes em WebP: miniatura (400px de largura, leve para o feed em conexão ruim) e completa
 * (1600px, para inspecionar tricomas). A altura é limitada a 2× a largura, para que um panorama vertical não gere
 * um arquivo gigante (nem passe do limite de 16383px do WebP).
 * <p>
 * Defesas contra imagens maliciosas: formato detectado por magic bytes, limite de megapixels checado antes de
 * decodificar (decompression bomb), subamostragem na leitura e limite de decodificações simultâneas. O arquivo
 * enviado é sempre lido por decoders em Java puro (TwelveMonkeys/JDK); código nativo só codifica pixels nossos.
 */
@Component
public class ImageIoSanitizer implements ImageSanitizer {

    static final int FULL_MAX_WIDTH = 1600;
    static final int THUMBNAIL_MAX_WIDTH = 400;
    static final int MAX_HEIGHT_TO_WIDTH = 2;
    static final long MAX_SOURCE_PIXELS = 50_000_000L;
    private static final float FULL_QUALITY = 0.85f;
    private static final float THUMBNAIL_QUALITY = 0.70f;
    private static final int MAX_CONCURRENT_DECODES = 2;

    private final Semaphore decodePermits = new Semaphore(MAX_CONCURRENT_DECODES, true);
    private final ImageEncoder encoder;

    public ImageIoSanitizer() {
        // Registra os plugins TwelveMonkeys (JPEG CMYK, WebP) e o writer WebP também quando rodando do fat jar.
        ImageIO.scanForPlugins();
        ImageIO.setUseCache(false);
        this.encoder = ImageEncoder.detect();
    }

    /** Formato em que as imagens estão sendo gravadas (WebP, ou JPEG se o encoder nativo não carregou). */
    public ImageFormat outputFormat() {
        return encoder.format();
    }

    @Override
    public SanitizedImage sanitize(byte[] content) {
        SourceFormat format = SourceFormat.detect(content)
                .orElseThrow(() -> new InvalidImageException("Formato não suportado. Envie JPEG, PNG ou WebP."));
        int orientation = ExifOrientation.read(content);

        BufferedImage upright = ExifOrientation.apply(decode(content, format, orientation), orientation);
        BufferedImage full = fitWithin(upright, FULL_MAX_WIDTH);
        BufferedImage thumbnail = fitWithin(full, THUMBNAIL_MAX_WIDTH);

        return new SanitizedImage(encoder.encode(full, FULL_QUALITY), full.getWidth(), full.getHeight(),
                encoder.encode(thumbnail, THUMBNAIL_QUALITY));
    }

    private BufferedImage decode(byte[] content, SourceFormat format, int orientation) {
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

            // Subamostra só o bastante para sobrar o dobro do tamanho final; o resize em etapas cuida do resto.
            // Com orientação 5-8 a foto ainda vai girar, então largura e altura finais são as trocadas.
            boolean swapsAxes = orientation >= 5;
            double scale = scaleToFit(swapsAxes ? height : width, swapsAxes ? width : height, FULL_MAX_WIDTH);
            int subsampling = Math.max(1, (int) Math.floor(1 / (2 * scale)));
            ImageReadParam param = reader.getDefaultReadParam();
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

    /**
     * Com o writer WebP há dois readers "webp" registrados; o TwelveMonkeys (Java puro) é preferido para que
     * o arquivo não confiável nunca passe por código nativo.
     */
    private static ImageReader readerFor(SourceFormat format, ImageInputStream input) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format.imageIoName());
        if (input == null || !readers.hasNext()) {
            throw new InvalidImageException("Formato não suportado. Envie JPEG, PNG ou WebP.");
        }
        ImageReader chosen = readers.next();
        while (readers.hasNext() && !isPureJava(chosen)) {
            chosen.dispose();
            chosen = readers.next();
        }
        if (format == SourceFormat.WEBP && !isPureJava(chosen)) {
            chosen.dispose();
            throw new InvalidImageException("Formato não suportado. Envie JPEG, PNG ou WebP.");
        }
        return chosen;
    }

    private static boolean isPureJava(ImageReader reader) {
        String name = reader.getClass().getName();
        return name.startsWith("com.twelvemonkeys.") || name.startsWith("com.sun.imageio.");
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

    /** Fator (≤ 1) para caber em {@code maxWidth} de largura e {@code 2 × maxWidth} de altura; nunca amplia. */
    static double scaleToFit(int width, int height, int maxWidth) {
        return Math.min(1.0, Math.min((double) maxWidth / width, (double) maxWidth * MAX_HEIGHT_TO_WIDTH / height));
    }

    /** Redução em etapas de 50% + ajuste final bicúbico: qualidade próxima de um resize "de verdade", sem libs nativas. */
    private static BufferedImage fitWithin(BufferedImage source, int maxWidth) {
        double scale = scaleToFit(source.getWidth(), source.getHeight(), maxWidth);
        if (scale >= 1.0) {
            return source;
        }
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
}
