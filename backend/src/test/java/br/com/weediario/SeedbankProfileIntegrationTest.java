package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import br.com.weediario.support.TestImages;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeedbankProfileIntegrationTest extends IntegrationTestSupport {

    @Test
    void seedbankPageListsEachStrainWithCoverAndLatestDiaries() throws Exception {
        Session grower = newGrower();
        String bank = "Banco " + UUID.randomUUID().toString().substring(0, 6);
        UUID first = diary(grower, "Zkittlez", bank, "PUBLIC", 20);
        UUID second = diary(grower, "Gelato 33", bank, "PUBLIC", 10);
        diary(grower, "Gelato 33", bank, "PUBLIC", 5);
        UUID hidden = diary(grower, "Segredo Kush", bank, "PRIVATE", 3);
        UUID photo = publishWithPhoto(grower, second);
        publishWithPhoto(grower, hidden);
        String breederId = body(mvc.perform(get("/api/v1/catalog/breeders/suggest").param("q", bank)).andReturn())
                .get(0).get("id").asText();

        JsonNode strains = body(mvc.perform(get("/api/v1/catalog/breeders/{id}/strains", breederId))
                .andExpect(status().isOk()).andReturn());
        List<String> names = names(strains.get("items"), "name");
        // Uma strain por card, a mais cultivada primeiro. A strain do diário privado existe no catálogo,
        // mas não conta cultivo nem empresta a foto.
        assertThat(names).containsExactly("Gelato 33", "Zkittlez", "Segredo Kush");
        JsonNode gelato = strains.get("items").get(0);
        assertThat(gelato.get("diaries").asInt()).isEqualTo(2);
        assertThat(gelato.get("coverThumbnailUrl").asText())
                .isEqualTo("/api/v1/diaries/%s/photos/%s?size=THUMBNAIL".formatted(second, photo));
        JsonNode secret = strains.get("items").get(2);
        assertThat(secret.get("diaries").asInt()).isZero();
        assertThat(secret.has("coverThumbnailUrl") && !secret.get("coverThumbnailUrl").isNull()).isFalse();

        JsonNode latest = body(mvc.perform(get("/api/v1/feed/diaries?breederId={id}&sort=NEWEST&size=6", breederId))
                .andExpect(status().isOk()).andReturn());
        assertThat(latest.get("totalItems").asInt()).isEqualTo(3);
        assertThat(latest.get("items").get(2).get("id").asText()).isEqualTo(first.toString());
        assertThat(names(latest.get("items"), "id")).doesNotContain(hidden.toString());
    }

    @Test
    void unknownSeedbankIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/catalog/breeders/{id}/strains", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    private UUID diary(Session session, String strain, String breeder, String visibility, int daysAgo) throws Exception {
        return UUID.fromString(body(mvc.perform(authed(post("/api/v1/diaries"), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","strainName":"%s","breeder":"%s","floweringType":"PHOTOPERIOD",
                                 "seedSex":"FEMINIZED","medium":"MINERAL","visibility":"%s","startedOn":"%s"}"""
                                .formatted(strain, strain, breeder, visibility, LocalDate.now().minusDays(daysAgo))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText());
    }

    private UUID publishWithPhoto(Session session, UUID diaryId) throws Exception {
        UUID photoId = uploadPhoto(session, TestImages.encode(TestImages.twoColor(300, 200), "png"));
        mvc.perform(authed(post("/api/v1/diaries/{id}/updates", diaryId), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"VEGETATIVE\",\"photoIds\":[\"%s\"]}".formatted(photoId)))
                .andExpect(status().isCreated());
        return photoId;
    }

    private static List<String> names(JsonNode items, String field) {
        List<String> values = new ArrayList<>();
        items.forEach(item -> values.add(item.get(field).asText()));
        return values;
    }
}
