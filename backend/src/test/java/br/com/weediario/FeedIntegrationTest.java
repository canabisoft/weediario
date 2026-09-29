package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeedIntegrationTest extends IntegrationTestSupport {

    @Test
    void feedShowsOnlyPublicDiariesAndFiltersByStage() throws Exception {
        String strain = "FeedStrain" + UUID.randomUUID().toString().substring(0, 6);
        Session grower = newGrower();
        UUID flowering = createDiary(grower, strain, "PUBLIC", LocalDate.now().minusDays(60).toString());
        UUID vegetative = createDiary(grower, strain, "PUBLIC", LocalDate.now().minusDays(20).toString());
        UUID hidden = createDiary(grower, strain, "PRIVATE", LocalDate.now().minusDays(20).toString());
        publish(grower, flowering, "FLOWERING");
        publish(grower, vegetative, "VEGETATIVE");

        List<String> all = ids(feed("?strain=" + strain));
        assertThat(all).containsExactlyInAnyOrder(flowering.toString(), vegetative.toString());
        assertThat(all).doesNotContain(hidden.toString());

        JsonNode floweringOnly = feed("?strain=" + strain + "&stage=FLOWERING");
        assertThat(ids(floweringOnly)).containsExactly(flowering.toString());
        JsonNode card = floweringOnly.get("items").get(0);
        assertThat(card.get("author").asText()).isEqualTo(grower.username());
        assertThat(card.get("currentWeek").asInt()).isEqualTo(9);
        assertThat(card.has("ownerId")).isFalse();
    }

    @Test
    void likeWildcardsInSearchAreTreatedAsText() throws Exception {
        JsonNode result = feed("?strain=%25");

        assertThat(result.get("items")).isEmpty();
    }

    @Test
    void myDiariesIncludePrivateOnes() throws Exception {
        Session grower = newGrower();
        UUID hidden = createDiary(grower, "Segredo", "PRIVATE", LocalDate.now().toString());

        JsonNode mine = body(mvc.perform(authed(get("/api/v1/me/diaries"), grower))
                .andExpect(status().isOk()).andReturn());

        assertThat(ids(mine)).containsExactly(hidden.toString());
        mvc.perform(get("/api/v1/me/diaries")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/diaries/{id}", hidden)).andExpect(status().isNotFound());
    }

    @Test
    void pageSizeIsCapped() throws Exception {
        assertThat(feed("?size=10000").get("size").asInt()).isEqualTo(50);
    }

    @Test
    void invalidFilterIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/feed/diaries?stage=HACKED")).andExpect(status().isBadRequest());
    }

    private void publish(Session grower, UUID diaryId, String stage) throws Exception {
        mvc.perform(authed(post("/api/v1/diaries/{id}/updates", diaryId), grower)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"%s","note":"update"}""".formatted(stage)))
                .andExpect(status().isCreated());
    }

    private JsonNode feed(String query) throws Exception {
        return body(mvc.perform(get("/api/v1/feed/diaries" + query)).andExpect(status().isOk()).andReturn());
    }

    private static List<String> ids(JsonNode page) {
        return StreamSupport.stream(page.get("items").spliterator(), false).map(item -> item.get("id").asText()).toList();
    }
}
