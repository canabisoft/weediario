package br.com.weediario;

import br.com.weediario.media.infrastructure.config.MediaConfig.ImageSweepJanitor;
import br.com.weediario.support.IntegrationTestSupport;
import br.com.weediario.support.TestImages;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Nenhuma foto fica no servidor sem uso: sobras de exclusão e fotos desvinculadas somem na varredura diária. */
class ImageSweepIntegrationTest extends IntegrationTestSupport {

    private static final Path STORAGE = Path.of("target/test-media").toAbsolutePath();

    @Autowired
    private ImageSweepJanitor sweep;

    @Test
    void removesFilesWithoutRecordAndPhotosNothingUsesButKeepsPublishedOnes() throws Exception {
        Session grower = newGrower();
        UUID diaryId = createDiary(grower, "Sweep Kush", "PUBLIC", LocalDate.now().minusDays(20).toString());
        UUID kept = publishPhoto(grower, diaryId);
        UUID unused = publishPhoto(grower, diaryId);

        // Foto que deixou de ser usada (como se o update tivesse sido apagado) há mais de um dia.
        jdbc.update("delete from diary_update_photos where image_id = ?", unused);
        // Sobra de uma conta apagada: arquivo em disco sem registro no banco.
        UUID leftover = UUID.randomUUID();
        Path leftoverFile = STORAGE.resolve(leftover.toString().substring(0, 2))
                .resolve(leftover + "-full.webp");
        Files.createDirectories(leftoverFile.getParent());
        Files.write(leftoverFile, new byte[]{1, 2, 3});
        makeOld(kept, unused, leftover);

        sweep.sweep();

        assertThat(filesOf(leftover)).isEmpty();
        assertThat(filesOf(unused)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from images where id = ?", Long.class, unused)).isZero();
        assertThat(filesOf(kept)).isNotEmpty();
        assertThat(jdbc.queryForObject("select count(*) from images where id = ?", Long.class, kept)).isOne();
    }

    private UUID publishPhoto(Session session, UUID diaryId) throws Exception {
        UUID photoId = uploadPhoto(session, TestImages.encode(TestImages.twoColor(200, 200), "png"));
        mvc.perform(authed(post("/api/v1/diaries/{id}/updates", diaryId), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"VEGETATIVE","photoIds":["%s"]}""".formatted(photoId)))
                .andExpect(status().isCreated());
        return photoId;
    }

    private void makeOld(UUID... ids) throws IOException {
        FileTime twoDaysAgo = FileTime.from(Instant.now().minus(Duration.ofDays(2)));
        for (UUID id : ids) {
            jdbc.update("update images set created_at = now() - interval '2 days' where id = ?", id);
            for (Path file : filesOf(id)) {
                Files.setLastModifiedTime(file, twoDaysAgo);
            }
        }
    }

    private static List<Path> filesOf(UUID id) throws IOException {
        Path dir = STORAGE.resolve(id.toString().substring(0, 2));
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(file -> file.getFileName().toString().startsWith(id.toString())).toList();
        }
    }
}
