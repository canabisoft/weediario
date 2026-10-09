package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import br.com.weediario.support.TestImages;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommunityFeaturesIntegrationTest extends IntegrationTestSupport {

    @Test
    void passwordBruteForceIsSlowedDownWithoutStoringUsernameOrIp() throws Exception {
        // Senha escolhida por gente pode ser adivinhada: depois de 5 erros, espera progressiva (30 s, 1 min...).
        String username = "alvo_" + UUID.randomUUID().toString().substring(0, 8);
        String wrong = loginJson(username, "senha errada demais");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        List<String> keys = jdbc.queryForList("select attempt_key from login_attempts", String.class);
        assertThat(keys).isNotEmpty().noneMatch(key -> key.contains("alvo_"));
    }

    @Test
    void harvestFeedsGramsPerPlantInTheRanking() throws Exception {
        Session grower = newGrower();
        UUID diaryId = createDiary(grower, "Ranking Kush", "PUBLIC", LocalDate.now().minusDays(100).toString());

        mvc.perform(authed(post("/api/v1/diaries/{id}/complete", diaryId), grower)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.harvest").doesNotExist());
        // Peso informado depois da cura.
        mvc.perform(authed(put("/api/v1/diaries/{id}/harvest", diaryId), grower)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"dryGrams":450,"plants":3}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.harvest.dryGrams").value(450));

        JsonNode ranking = body(mvc.perform(signedIn(get("/api/v1/growers/ranking?sort=GRAMS_PER_PLANT&q=" + grower.username())))
                .andExpect(status().isOk()).andReturn());
        JsonNode entry = ranking.get("items").get(0);
        assertThat(entry.get("username").asText()).isEqualTo(grower.username());
        assertThat(entry.get("gramsPerPlant").decimalValue()).isEqualByComparingTo("150.0");
        assertThat(entry.get("diaries").asInt()).isEqualTo(1);
    }

    @Test
    void harvestNeedsWeightAndPlantsTogether() throws Exception {
        Session grower = newGrower();
        UUID diaryId = createDiary(grower, "Half", "PUBLIC", LocalDate.now().minusDays(10).toString());

        mvc.perform(authed(post("/api/v1/diaries/{id}/complete", diaryId), grower)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"dryGrams":100}"""))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void privateDiariesStayOutOfPublicStats() throws Exception {
        Session grower = newGrower();
        createDiary(grower, "Secret", "PRIVATE", LocalDate.now().toString());

        mvc.perform(get("/api/v1/growers/{u}", grower.username()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diaries").value(0));
    }

    @Test
    void profileWithBioAndSanitizedAvatar() throws Exception {
        Session grower = newGrower();
        UUID photo = uploadPhoto(grower, TestImages.phoneJpegWithExif(600, 600, (short) 1));

        mvc.perform(authed(put("/api/v1/me/profile"), grower).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bio":"Cultivo indoor desde 2019."}"""))
                .andExpect(status().isOk());
        mvc.perform(authed(put("/api/v1/me/avatar"), grower).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageId\":\"" + photo + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAvatar").value(true));

        mvc.perform(get("/api/v1/growers/{u}", grower.username()))
                .andExpect(jsonPath("$.bio").value("Cultivo indoor desde 2019."))
                .andExpect(jsonPath("$.hasAvatar").value(true))
                .andExpect(jsonPath("$.email").doesNotExist());

        byte[] avatar = mvc.perform(get("/api/v1/growers/{u}/avatar", grower.username()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        var metadata = com.drew.imaging.ImageMetadataReader.readMetadata(new ByteArrayInputStream(avatar), avatar.length);
        assertThat(metadata.getFirstDirectoryOfType(com.drew.metadata.exif.GpsDirectory.class)).isNull();

        // Não existe e-mail, nem para o próprio usuário.
        mvc.perform(authed(get("/api/v1/me"), grower)).andExpect(jsonPath("$.email").doesNotExist());

        mvc.perform(authed(delete("/api/v1/me/avatar"), grower)).andExpect(jsonPath("$.hasAvatar").value(false));
        mvc.perform(get("/api/v1/growers/{u}/avatar", grower.username())).andExpect(status().isNotFound());
    }

    @Test
    void followingFeedsThePersonalFeedAndOnlyCountsArePublic() throws Exception {
        Session author = newGrower();
        Session fan = newGrower();
        String strain = "Follow" + UUID.randomUUID().toString().substring(0, 6);
        UUID followedDiary = createDiary(author, strain, "PUBLIC", LocalDate.now().toString());
        createDiary(newGrower(), strain, "PUBLIC", LocalDate.now().toString());

        mvc.perform(authed(put("/api/v1/growers/{u}/follow", author.username()), fan)).andExpect(status().isNoContent());
        mvc.perform(authed(put("/api/v1/growers/{u}/follow", author.username()), fan)).andExpect(status().isNoContent());

        mvc.perform(authed(get("/api/v1/growers/{u}", author.username()), fan))
                .andExpect(jsonPath("$.followers").value(1))
                .andExpect(jsonPath("$.followedByViewer").value(true));
        mvc.perform(get("/api/v1/growers/{u}", author.username()))
                .andExpect(jsonPath("$.followedByViewer").value(false));

        JsonNode feed = body(mvc.perform(authed(get("/api/v1/feed/diaries?following=true&strain=" + strain), fan))
                .andExpect(status().isOk()).andReturn());
        assertThat(feed.get("items")).hasSize(1);
        assertThat(feed.get("items").get(0).get("id").asText()).isEqualTo(followedDiary.toString());

        mvc.perform(get("/api/v1/feed/diaries?author=" + author.username().toUpperCase()))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(followedDiary.toString()));

        mvc.perform(get("/api/v1/feed/diaries?following=true")).andExpect(status().isUnprocessableEntity());
        mvc.perform(authed(get("/api/v1/me/following"), fan)).andExpect(jsonPath("$[0].username").value(author.username()));
        mvc.perform(authed(put("/api/v1/growers/{u}/follow", fan.username()), fan))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(authed(delete("/api/v1/growers/{u}/follow", author.username()), fan)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/growers/{u}", author.username())).andExpect(jsonPath("$.followers").value(0));
    }

    @Test
    void reservedUsernamesCannotBeRegistered() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("ranking")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void seedbankIsRatedAlongWithTheStrain() throws Exception {
        Session grower = newGrower();
        UUID diaryId = createDiary(grower, "Seedbank Haze", "PUBLIC", LocalDate.now().minusDays(90).toString());
        mvc.perform(authed(post("/api/v1/diaries/{id}/complete", diaryId), grower)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"dryGrams":300,"plants":2}"""))
                .andExpect(status().isOk());

        mvc.perform(authed(post("/api/v1/diaries/{id}/review", diaryId), grower)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"vigor":5,"pestResistance":4,"easeOfGrowth":4,"yield":5,"terpeneProfile":5,
                                 "seedbankGermination":5,"seedbankStability":4,"seedbankService":3,
                                 "report":"Todas germinaram em 48h, fenótipos bem parecidos."}"""))
                .andExpect(status().isCreated());

        // createDiary usa o breeder "Banco Teste".
        JsonNode seedbanks = body(mvc.perform(get("/api/v1/seedbanks?query=banco teste")).andExpect(status().isOk()).andReturn());
        JsonNode seedbank = seedbanks.get("items").get(0);
        assertThat(seedbank.get("name").asText()).isEqualTo("Banco Teste");
        String seedbankId = seedbank.get("id").asText();

        mvc.perform(get("/api/v1/seedbanks/{id}/reviews", seedbankId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].strain").value("Seedbank Haze"))
                .andExpect(jsonPath("$.items[0].germination").value(5));
    }
}
