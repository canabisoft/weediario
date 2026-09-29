package br.com.weediario.media.infrastructure.imaging;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

/**
 * Celulares gravam a foto "deitada" e indicam a rotação na tag EXIF Orientation. Como todo o EXIF é descartado,
 * a rotação precisa ser aplicada nos pixels antes, senão a foto aparece girada.
 * <p>
 * Esta é a <strong>única</strong> informação lida dos metadados, e ela não é armazenada.
 */
final class ExifOrientation {

    static final int NORMAL = 1;

    private ExifOrientation() {
    }

    static int read(byte[] content) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(content), content.length);
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                int orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                return orientation >= 1 && orientation <= 8 ? orientation : NORMAL;
            }
        } catch (Exception malformedOrMissing) {
            // EXIF corrompido não impede o upload: a imagem só não é rotacionada.
        }
        return NORMAL;
    }

    static BufferedImage apply(BufferedImage source, int orientation) {
        if (orientation == NORMAL) {
            return source;
        }
        int w = source.getWidth();
        int h = source.getHeight();
        boolean swapsAxes = orientation >= 5;
        // Cada transformação mapeia (x, y) da imagem gravada para a posição correta na imagem de saída.
        AffineTransform transform = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);   // espelhada na horizontal
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);  // 180°
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);   // espelhada na vertical
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);    // transposta
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);   // 90° horário
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);  // transversa
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);   // 90° anti-horário
            default -> new AffineTransform();
        };
        BufferedImage target = new BufferedImage(swapsAxes ? h : w, swapsAxes ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.drawImage(source, transform, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }
}
