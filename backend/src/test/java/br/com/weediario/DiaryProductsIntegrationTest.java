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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DiaryProductsIntegrationTest extends IntegrationTestSupport {

    @Test
    void nutrientAddedToAnExistingDiaryShowsUpInProductRanking() throws Exception {
        Session grower = newGrower();
        UUID diaryId = createDiary(grower, "Gorilla Glue", "PUBLIC", LocalDate.now().minusDays(10).toString());
        String productId = newProduct(grower, "NUTRIENT");

        // O cultivo foi aberto sem setup: o nutriente é informado depois, direto no diário.
        mvc.perform(authed(put("/api/v1/diaries/{id}/products", diaryId), grower)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[\"%s\",\"%s\"]}".formatted(productId, productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(productId));

        mvc.perform(get("/api/v1/diaries/{id}", diaryId))
                .andExpect(jsonPath("$.setup.products[0].id").value(productId))
                .andExpect(jsonPath("$.setup.name").doesNotExist());
        JsonNode row = rankingRow(productId);
        assertThat(row).isNotNull();
        assertThat(row.get("diaries").asInt()).isEqualTo(1);

        // Remover também tira do ranking.
        mvc.perform(authed(put("/api/v1/diaries/{id}/products", diaryId), grower)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productIds\":[]}"))
                .andExpect(status().isOk());
        assertThat(rankingRow(productId)).isNull();
    }

    @Test
    void onlyTheAuthorChangesProducts() throws Exception {
        Session owner = newGrower();
        Session stranger = newGrower();
        UUID diaryId = createDiary(owner, "Gelato", "PUBLIC", LocalDate.now().minusDays(3).toString());
        String productId = newProduct(stranger, "ADDITIVE");

        mvc.perform(authed(put("/api/v1/diaries/{id}/products", diaryId), stranger)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productIds\":[\"%s\"]}".formatted(productId)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/diaries/{id}/products", diaryId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productIds\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsProductsThatAreNotInTheCatalog() throws Exception {
        Session grower = newGrower();
        UUID diaryId = createDiary(grower, "Amnesia", "PUBLIC", LocalDate.now().minusDays(3).toString());

        mvc.perform(authed(put("/api/v1/diaries/{id}/products", diaryId), grower)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productIds\":[\"%s\"]}".formatted(UUID.randomUUID())))
                .andExpect(status().is4xxClientError());
    }

    private String newProduct(Session session, String category) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String brandId = body(mvc.perform(authed(post("/api/v1/catalog/brands/resolve"), session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Marca " + suffix + "\"}"))
                .andReturn()).get("item").get("id").asText();
        return body(mvc.perform(authed(post("/api/v1/catalog/products/resolve"), session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"brandId":"%s","name":"Flora %s","category":"%s"}""".formatted(brandId, suffix, category)))
                .andReturn()).get("item").get("id").asText();
    }

    private JsonNode rankingRow(String productId) throws Exception {
        JsonNode items = body(mvc.perform(get("/api/v1/catalog/rankings/PRODUCTS?size=100")).andReturn()).get("items");
        for (JsonNode row : items) {
            if (row.get("id").asText().equals(productId)) {
                return row;
            }
        }
        return null;
    }
}
