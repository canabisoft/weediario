package br.com.weediario.media.infrastructure.imaging;

import br.com.weediario.media.application.exception.InvalidImageException;
import br.com.weediario.media.application.port.ImageSanitizer.SanitizedImage;
import br.com.weediario.support.TestImages;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.StreamSupport;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageIoSanitizerTest {

    private final ImageIoSanitizer sanitizer = new ImageIoSanitizer();

    @Test
    void removesGpsDeviceAndCaptureDate() throws Exception {
        byte[] original = TestImages.phoneJpegWithExif(400, 300, (short) 1);
        assertThat(readMetadata(original).getFirstDirectoryOfType(GpsDirectory.class))
                .as("a foto de teste precisa ter GPS para o teste valer").isNotNull();

        SanitizedImage result = sanitizer.sanitize(original);

        for (byte[] output : List.of(result.full(), result.thumbnail())) {
            List<String> directories = directoryNames(readMetadata(output));
            assertThat(directories).noneMatch(name -> name.startsWith("Exif") || name.startsWith("GPS")
                    || name.startsWith("XMP") || name.startsWith("IPTC") || name.startsWith("ICC"));
            String raw = new String(output, StandardCharsets.ISO_8859_1);
            assertThat(raw).doesNotContain("iPhone", "Apple", "2026:09:01");
        }
    }

    @Test
    void appliesExifOrientationToPixels() throws Exception {
        // Orientação 6 = a câmera gravou deitado; a foto deve ser girada 90° no sentido horário.
        byte[] original = TestImages.phoneJpegWithExif(400, 200, (short) 6);

        SanitizedImage result = sanitizer.sanitize(original);
        BufferedImage output = ImageIO.read(new ByteArrayInputStream(result.full()));

        assertThat(output.getWidth()).isEqualTo(200);
        assertThat(output.getHeight()).isEqualTo(400);
        // A metade esquerda (vermelha) do original vai para cima após o giro horário.
        assertThat(isMostly(new Color(output.getRGB(100, 50)), Color.RED)).isTrue();
        assertThat(isMostly(new Color(output.getRGB(100, 350)), Color.BLUE)).isTrue();
        assertThat(readMetadata(result.full()).getFirstDirectoryOfType(ExifIFD0Directory.class)).isNull();
    }

    @Test
    void downscalesToFullAndThumbnailSizes() throws Exception {
        byte[] original = TestImages.encode(TestImages.twoColor(4000, 3000), "jpeg");

        SanitizedImage result = sanitizer.sanitize(original);

        assertThat(result.width()).isEqualTo(ImageIoSanitizer.FULL_MAX_EDGE);
        assertThat(result.height()).isEqualTo(1536);
        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(result.thumbnail()));
        assertThat(Math.max(thumbnail.getWidth(), thumbnail.getHeight())).isEqualTo(ImageIoSanitizer.THUMBNAIL_MAX_EDGE);
    }

    @Test
    void convertsTransparentPngToJpeg() {
        BufferedImage transparent = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);

        SanitizedImage result = sanitizer.sanitize(TestImages.encode(transparent, "png"));

        assertThat(result.full()).startsWith((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
    }

    @Test
    void rejectsContentThatIsNotAnImage() {
        byte[] script = "<?php system($_GET['c']); ?>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> sanitizer.sanitize(script))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("Formato não suportado");
    }

    @Test
    void rejectsCorruptedImageWithValidSignature() {
        byte[] fake = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02, 0x03};

        assertThatThrownBy(() -> sanitizer.sanitize(fake)).isInstanceOf(InvalidImageException.class);
    }

    @Test
    void rejectsDecompressionBombBeforeDecoding() throws IOException {
        byte[] bomb = pngHeaderOnly(100_000, 100_000);

        assertThatThrownBy(() -> sanitizer.sanitize(bomb))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("Resolução");
    }

    private static Metadata readMetadata(byte[] content) throws Exception {
        return ImageMetadataReader.readMetadata(new ByteArrayInputStream(content), content.length);
    }

    private static List<String> directoryNames(Metadata metadata) {
        return StreamSupport.stream(metadata.getDirectories().spliterator(), false).map(Directory::getName).toList();
    }

    private static boolean isMostly(Color actual, Color expected) {
        return Math.abs(actual.getRed() - expected.getRed()) < 60
                && Math.abs(actual.getGreen() - expected.getGreen()) < 60
                && Math.abs(actual.getBlue() - expected.getBlue()) < 60;
    }

    /** PNG com cabeçalho IHDR válido anunciando dimensões gigantes, sem os dados de pixel. */
    private static byte[] pngHeaderOnly(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        byte[] ihdr = ByteBuffer.allocate(13).putInt(width).putInt(height)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0).array();
        writeChunk(out, "IHDR", ihdr);
        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.write(ByteBuffer.allocate(4).putInt(data.length).array());
        out.write(typeBytes);
        out.write(data);
        out.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }
}
