package br.com.weediario.support;

import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Gera fotos de teste como as de um celular: com GPS, marca/modelo, data de captura e orientação.
 */
public final class TestImages {

    public static final double LATITUDE = -23.5505;
    public static final double LONGITUDE = -46.6333;

    private TestImages() {
    }

    /** Metade esquerda vermelha, metade direita azul. */
    public static BufferedImage twoColor(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, width / 2, height);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(width / 2, 0, width - width / 2, height);
        graphics.dispose();
        return image;
    }

    public static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] phoneJpegWithExif(int width, int height, short orientation) {
        try {
            TiffOutputSet exif = new TiffOutputSet();
            TiffOutputDirectory root = exif.getOrCreateRootDirectory();
            root.add(TiffTagConstants.TIFF_TAG_MAKE, "Apple");
            root.add(TiffTagConstants.TIFF_TAG_MODEL, "iPhone 15 Pro");
            root.add(TiffTagConstants.TIFF_TAG_ORIENTATION, orientation);
            exif.getOrCreateExifDirectory().add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, "2026:09:01 22:13:05");
            exif.setGpsInDegrees(LONGITUDE, LATITUDE);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            new ExifRewriter().updateExifMetadataLossless(encode(twoColor(width, height), "jpeg"), out, exif);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
