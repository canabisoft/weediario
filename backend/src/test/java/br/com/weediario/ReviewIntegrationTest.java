package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewIntegrationTest extends IntegrationTestSupport {

    private static final String REVIEW = """
            {"vigor":%d,"pestResistance":4,"easeOfGrowth":4,"yield":%d,"terpeneProfile":5,
             "report":"Flora de 9 semanas, aroma de limão, rendimento acima do esperado."}""";

    @Test
    void completedGrowsFeedTheStrainCatalog() throws Exception {
        String strain = "Review Kush " + UUID.randomUUID().toString().substring(0, 6);
        Session first = newGrower();
        Session second = newGrower();
        UUID firstDiary = createDiary(first, strain, "PUBLIC", LocalDate.now().minusDays(90).toString());
        // Mesma genética digitada de outro jeito: deve cair na mesma strain do catálogo.
        UUID secondDiary = createDiary(second, strain.toUpperCase().replace(" ", "-"), "PRIVATE",
                LocalDate.now().minusDays(90).toString());

        mvc.perform(authed(post("/api/v1/diaries/{id}/review", firstDiary), first)
                        .contentType(MediaType.APPLICATION_JSON).content(REVIEW.formatted(5, 5)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Só é possível avaliar a genética depois de concluir o cultivo"));

        complete(first, firstDiary);
        complete(second, secondDiary);
        JsonNode submitted = body(mvc.perform(authed(post("/api/v1/diaries/{id}/review", firstDiary), first)
                        .contentType(MediaType.APPLICATION_JSON).content(REVIEW.formatted(5, 5)))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(authed(post("/api/v1/diaries/{id}/review", secondDiary), second)
                        .contentType(MediaType.APPLICATION_JSON).content(REVIEW.formatted(3, 3)))
                .andExpect(status().isCreated());
        String strainId = submitted.get("strainId").asText();

        // Visitante anônimo consulta o catálogo.
        mvc.perform(get("/api/v1/strains/{id}", strainId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount").value(2))
                .andExpect(jsonPath("$.vigor").value(4.0))
                .andExpect(jsonPath("$.overall").value(4.2));

        JsonNode reviews = body(mvc.perform(get("/api/v1/strains/{id}/reviews", strainId))
                .andExpect(status().isOk()).andReturn());
        assertThat(reviews.get("totalItems").asInt()).isEqualTo(2);
        for (JsonNode review : reviews.get("items")) {
            if (review.get("author").asText().equals(second.username())) {
                assertThat(review.hasNonNull("diaryId")).as("review de diário privado não aponta para ele").isFalse();
            } else {
                assertThat(review.get("diaryId").asText()).isEqualTo(firstDiary.toString());
            }
        }

        JsonNode search = body(mvc.perform(get("/api/v1/strains?query=" + strain.substring(0, 12)))
                .andExpect(status().isOk()).andReturn());
        assertThat(search.get("items")).anyMatch(item -> item.get("id").asText().equals(strainId));
    }

    @Test
    void oneReviewPerGrowAndOnlyByItsAuthor() throws Exception {
        Session owner = newGrower();
        Session stranger = newGrower();
        UUID diaryId = createDiary(owner, "Solo Review", "PUBLIC", LocalDate.now().minusDays(80).toString());
        complete(owner, diaryId);

        mvc.perform(authed(post("/api/v1/diaries/{id}/review", diaryId), stranger)
                        .contentType(MediaType.APPLICATION_JSON).content(REVIEW.formatted(4, 4)))
                .andExpect(status().isNotFound());
        mvc.perform(authed(post("/api/v1/diaries/{id}/review", diaryId), owner)
                        .contentType(MediaType.APPLICATION_JSON).content(REVIEW.formatted(4, 4)))
                .andExpect(status().isCreated());
        mvc.perform(authed(post("/api/v1/diaries/{id}/review", diaryId), owner)
                        .contentType(MediaType.APPLICATION_JSON).content(REVIEW.formatted(4, 4)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void strainPageTellsWhichOfMyGrowsCanBeReviewedAndReviewsCountInTheRanking() throws Exception {
        String strain = "Page Kush " + UUID.randomUUID().toString().substring(0, 6);
        Session grower = newGrower();
        UUID done = createDiary(grower, strain, "PRIVATE", LocalDate.now().minusDays(120).toString());
        UUID growing = createDiary(grower, strain, "PRIVATE", LocalDate.now().minusDays(10).toString());
        complete(grower, done);
        String strainId = jdbc.queryForObject("select strain_id from grow_diaries where id = ?", String.class, done);

        // A página da strain é pública, mas "meus cultivos" exige conta.
        mvc.perform(get("/api/v1/me/strains/{id}/grows", strainId)).andExpect(status().isUnauthorized());
        JsonNode grows = body(mvc.perform(authed(get("/api/v1/me/strains/{id}/grows", strainId), grower))
                .andExpect(status().isOk()).andReturn());
        assertThat(grows).hasSize(2);
        assertThat(grows.get(0).get("diaryId").asText()).isEqualTo(growing.toString());
        assertThat(grows.get(0).get("completed").asBoolean()).isFalse();
        assertThat(grows.get(1).get("completed").asBoolean()).isTrue();
        assertThat(grows.get(1).get("reviewed").asBoolean()).isFalse();
        // Quem não cultivou a strain não vê nada.
        mvc.perform(authed(get("/api/v1/me/strains/{id}/grows", strainId), newGrower()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(authed(post("/api/v1/diaries/{id}/review", done), grower)
                        .contentType(MediaType.APPLICATION_JSON).content(REVIEW.formatted(4, 4)))
                .andExpect(status().isCreated());
        mvc.perform(authed(get("/api/v1/me/strains/{id}/grows", strainId), grower))
                .andExpect(jsonPath("$[1].reviewed").value(true));

        // Diários privados não aparecem no ranking de diários, mas a avaliação (que é pública) conta.
        JsonNode ranking = body(mvc.perform(signedIn(get("/api/v1/growers/ranking?sort=REVIEWS&q=" + grower.username())))
                .andExpect(status().isOk()).andReturn());
        assertThat(ranking.get("items").get(0).get("username").asText()).isEqualTo(grower.username());
        assertThat(ranking.get("items").get(0).get("reviews").asInt()).isEqualTo(1);
        assertThat(ranking.get("items").get(0).get("diaries").asInt()).isZero();
        mvc.perform(get("/api/v1/growers/{u}", grower.username())).andExpect(jsonPath("$.reviews").value(1));
    }

    @Test
    void ratingsOutOfRangeAreRejected() throws Exception {
        Session owner = newGrower();
        UUID diaryId = createDiary(owner, "Range", "PUBLIC", LocalDate.now().minusDays(80).toString());

        mvc.perform(authed(post("/api/v1/diaries/{id}/review", diaryId), owner)
                        .contentType(MediaType.APPLICATION_JSON).content(REVIEW.formatted(9, 0)))
                .andExpect(status().isBadRequest());
    }

    private void complete(Session session, UUID diaryId) throws Exception {
        mvc.perform(authed(post("/api/v1/diaries/{id}/complete", diaryId), session)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
