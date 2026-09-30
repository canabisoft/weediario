package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import br.com.weediario.support.TestImages;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PhotoAndTimelineIntegrationTest extends IntegrationTestSupport {

    @Test
    void publishedPhotoHasNoExifAndFollowsDiaryVisibility() throws Exception {
        Session grower = newGrower();
        UUID diaryId = createDiary(grower, "Gelato 41", "PUBLIC", LocalDate.now().minusDays(20).toString());
        UUID photoId = uploadPhoto(grower, TestImages.phoneJpegWithExif(1200, 900, (short) 6));

        mvc.perform(authed(post("/api/v1/diaries/{id}/updates", diaryId), grower)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"VEGETATIVE","note":"Primeira poda apical",
                                 "temperatureCelsius":25.4,"relativeHumidity":60,"wateringPh":6.2,
                                 "photoIds":["%s"]}""".formatted(photoId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.day").value(21))
                .andExpect(jsonPath("$.week").value(3))
                .andExpect(jsonPath("$.photoIds[0]").value(photoId.toString()))
                .andExpect(jsonPath("$.photos[0].id").value(photoId.toString()))
                .andExpect(jsonPath("$.photos[0].thumbnailUrl")
                        .value("/api/v1/diaries/%s/photos/%s?size=THUMBNAIL".formatted(diaryId, photoId)))
                .andExpect(jsonPath("$.photos[0].fullUrl")
                        .value("/api/v1/diaries/%s/photos/%s?size=FULL".formatted(diaryId, photoId)));

        // Visitante anônimo vê a foto de um diário público, sem nenhum metadado de origem.
        MvcResult photo = mvc.perform(get("/api/v1/diaries/{d}/photos/{p}", diaryId, photoId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/webp"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        byte[] served = photo.getResponse().getContentAsByteArray();
        var metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(served), served.length);
        assertThat(metadata.getFirstDirectoryOfType(GpsDirectory.class)).isNull();
        assertThat(metadata.getFirstDirectoryOfType(ExifIFD0Directory.class)).isNull();
        assertThat(new String(served, StandardCharsets.ISO_8859_1)).doesNotContain("iPhone", "IMG_2041");

        // Ao tornar o diário privado, a mesma URL da foto passa a responder 404 para terceiros.
        mvc.perform(authed(put("/api/v1/diaries/{id}/visibility", diaryId), grower)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"visibility":"PRIVATE"}"""))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/diaries/{d}/photos/{p}", diaryId, photoId)).andExpect(status().isNotFound());
        mvc.perform(authed(get("/api/v1/diaries/{d}/photos/{p}", diaryId, photoId), grower))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private"));
    }

    @Test
    void photoCannotBeHijackedByAnotherGrower() throws Exception {
        Session owner = newGrower();
        Session attacker = newGrower();
        UUID photoId = uploadPhoto(owner, TestImages.phoneJpegWithExif(400, 300, (short) 1));
        UUID attackerDiary = createDiary(attacker, "Critical", "PUBLIC", LocalDate.now().toString());

        mvc.perform(authed(post("/api/v1/diaries/{id}/updates", attackerDiary), attacker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"GERMINATION","photoIds":["%s"]}""".formatted(photoId)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cannotPostToSomeoneElsesDiary() throws Exception {
        Session owner = newGrower();
        Session stranger = newGrower();
        UUID diaryId = createDiary(owner, "Critical", "PUBLIC", LocalDate.now().toString());

        mvc.perform(authed(post("/api/v1/diaries/{id}/updates", diaryId), stranger)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"stage":"VEGETATIVE","note":"não é meu diário"}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingUpdateRemovesPhotoAndRewindsStage() throws Exception {
        Session grower = newGrower();
        UUID diaryId = createDiary(grower, "Blue Dream", "PUBLIC", LocalDate.now().minusDays(40).toString());
        UUID photoId = uploadPhoto(grower, TestImages.encode(TestImages.twoColor(300, 300), "png"));

        MvcResult created = mvc.perform(authed(post("/api/v1/diaries/{id}/updates", diaryId), grower)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"FLOWERING","photoIds":["%s"]}""".formatted(photoId)))
                .andExpect(status().isCreated())
                .andReturn();
        String updateId = body(created).get("id").asText();
        mvc.perform(get("/api/v1/diaries/{id}", diaryId)).andExpect(jsonPath("$.currentStage").value("FLOWERING"));

        mvc.perform(authed(delete("/api/v1/diaries/{d}/updates/{u}", diaryId, updateId), grower))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/diaries/{d}/photos/{p}", diaryId, photoId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/diaries/{id}", diaryId)).andExpect(jsonPath("$.currentStage").value("GERMINATION"));
    }

    @Test
    void timelineIsPaginatedNewestFirst() throws Exception {
        Session grower = newGrower();
        UUID diaryId = createDiary(grower, "Zkittlez", "PUBLIC", LocalDate.now().minusDays(10).toString());
        for (int daysAgo = 9; daysAgo >= 0; daysAgo -= 3) {
            mvc.perform(authed(post("/api/v1/diaries/{id}/updates", diaryId), grower)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"stage":"VEGETATIVE","recordedOn":"%s","note":"dia %d"}"""
                                    .formatted(LocalDate.now().minusDays(daysAgo), daysAgo)))
                    .andExpect(status().isCreated());
        }

        JsonNode page = body(mvc.perform(get("/api/v1/diaries/{id}/updates?size=2", diaryId))
                .andExpect(status().isOk()).andReturn());

        assertThat(page.get("totalItems").asInt()).isEqualTo(4);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page.get("items").get(0).get("note").asText()).isEqualTo("dia 0");
    }

    @Test
    void rejectsNonImageUploadAndRequiresLogin() throws Exception {
        Session grower = newGrower();
        MockMultipartFile disguised = new MockMultipartFile("file", "foto.jpg", "image/jpeg",
                "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8));

        mvc.perform(authed(multipart("/api/v1/media/images").file(disguised), grower))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(multipart("/api/v1/media/images").file(disguised)).andExpect(status().isUnauthorized());
    }
}
