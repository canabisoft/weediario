package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecipeFeedbackIntegrationTest extends IntegrationTestSupport {

    @Test
    void growersRateOthersRecipesOnceAndTheAuthorCannotVoteForItself() throws Exception {
        Session author = newGrower();
        Session fan = newGrower();
        Session critic = newGrower();
        String id = recipe(author, "Avaliada " + token(), "MINERAL", "PUBLIC", null);

        rate(fan, id, 5).andExpect(status().isOk()).andExpect(jsonPath("$.mine").value(5));
        rate(critic, id, 3).andExpect(status().isOk());
        rate(fan, id, 4).andExpect(status().isOk()); // troca a nota, não soma outra
        rate(author, id, 5).andExpect(status().isUnprocessableEntity());
        rate(fan, id, 6).andExpect(status().isBadRequest());

        mvc.perform(signedIn(get("/api/v1/recipes/{id}/rating", id)))
                .andExpect(jsonPath("$.average").value(3.5))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.canRate").value(true)); // outro grower ainda pode dar a nota dele
        mvc.perform(authed(get("/api/v1/recipes/{id}/rating", id), author)).andExpect(jsonPath("$.canRate").value(false));
        mvc.perform(signedIn(get("/api/v1/recipes/{id}", id))).andExpect(jsonPath("$.recipe.ratingCount").value(2));

        mvc.perform(authed(delete("/api/v1/recipes/{id}/rating", id), critic))
                .andExpect(jsonPath("$.count").value(1)).andExpect(jsonPath("$.mine").doesNotExist());

        String hidden = recipe(author, "Privada " + token(), "MINERAL", "PRIVATE", null);
        rate(fan, hidden, 5).andExpect(status().isNotFound());
    }

    @Test
    void topRatedWeighsTheNumberOfVotes() throws Exception {
        Session author = newGrower();
        String tag = token();
        String lucky = recipe(author, "Sortuda " + tag, "ORGANIC", "PUBLIC", null);
        String solid = recipe(author, "Consistente " + tag, "ORGANIC", "PUBLIC", null);
        rate(newGrower(), lucky, 5);
        for (int i = 0; i < 4; i++) {
            rate(newGrower(), solid, 5);
        }

        // Uma nota 5 isolada não passa na frente de quatro notas 5.
        JsonNode page = body(mvc.perform(signedIn(get("/api/v1/recipes?q={q}&sort=TOP_RATED", tag))).andReturn());
        assertThat(ids(page.get("items"))).containsExactly(solid, lucky);
        assertThat(page.get("items").get(0).get("rating").asDouble()).isEqualTo(5.0);
        assertThat(page.get("items").get(0).get("ratingCount").asInt()).isEqualTo(4);
    }

    @Test
    void commentsAreModeratedByTheCommunityAndTheRecipeOwner() throws Exception {
        Session author = newGrower();
        Session asker = newGrower();
        String id = recipe(author, "Comentada " + token(), "MINERAL", "PUBLIC", null);

        String commentId = body(mvc.perform(authed(post("/api/v1/recipes/{id}/comments", id), asker)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Funciona em coco?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author").value(asker.username()))
                .andExpect(jsonPath("$.canDelete").value(true))
                .andExpect(jsonPath("$.canReport").value(false))
                .andReturn()).get("id").asText();
        mvc.perform(signedIn(get("/api/v1/recipes/{id}/comments", id)))
                .andExpect(jsonPath("$.items[0].body").value("Funciona em coco?"))
                .andExpect(jsonPath("$.items[0].canDelete").value(false))
                .andExpect(jsonPath("$.items[0].authorId").doesNotExist());
        report(asker, "RECIPE_COMMENT", commentId, "SPAM").andExpect(status().isUnprocessableEntity());

        // Três denúncias distintas ocultam o comentário para o público.
        for (int i = 0; i < 3; i++) {
            report(trustedGrower(), "RECIPE_COMMENT", commentId, "INAPPROPRIATE").andExpect(status().isOk());
        }
        mvc.perform(signedIn(get("/api/v1/recipes/{id}/comments", id))).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(authed(get("/api/v1/recipes/{id}/comments", id), moderator()))
                .andExpect(jsonPath("$.items[0].hidden").value(true));

        // O dono da receita apaga comentários nela.
        mvc.perform(authed(delete("/api/v1/recipes/{id}/comments/{c}", id, commentId), author))
                .andExpect(status().isNoContent());

        String privateId = recipe(author, "Sem conversa " + token(), "MINERAL", "PRIVATE", null);
        mvc.perform(authed(post("/api/v1/recipes/{id}/comments", privateId), author)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"nota\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void recommendsRecipesOfTheRightKindThatUseProductsTheGrowerAlreadyHas() throws Exception {
        Session grower = newGrower();
        Session community = newGrower();
        String product = newProduct(grower);
        UUID diaryId = mineralDiary(grower);
        mvc.perform(authed(put("/api/v1/diaries/{id}/products", diaryId), grower).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productIds\":[\"%s\"]}".formatted(product))).andExpect(status().isOk());

        String matching = recipe(community, "Usa o que você tem " + token(), "MINERAL", "PUBLIC", product);
        String organic = recipe(community, "Orgânica com o produto " + token(), "ORGANIC", "PUBLIC", product);
        String mine = recipe(grower, "Minha " + token(), "MINERAL", "PUBLIC", product);

        JsonNode suggestions = body(mvc.perform(authed(get("/api/v1/recipes/recommended?diaryId={d}", diaryId), grower))
                .andExpect(status().isOk()).andReturn());
        assertThat(suggestions.get(0).get("recipe").get("id").asText()).isEqualTo(matching);
        assertThat(suggestions.get(0).get("sharedProducts").asInt()).isEqualTo(1);
        List<String> suggested = new ArrayList<>();
        suggestions.forEach(item -> {
            suggested.add(item.get("recipe").get("id").asText());
            assertThat(item.get("recipe").get("kind").asText()).isEqualTo("MINERAL");
        });
        assertThat(suggested).doesNotContain(organic, mine);

        // Depois de usar no cultivo (ou replicar), a receita sai das sugestões.
        mvc.perform(authed(put("/api/v1/diaries/{d}/recipes", diaryId), grower).contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipeIds\":[\"%s\"]}".formatted(matching))).andExpect(status().isOk());
        JsonNode after = body(mvc.perform(authed(get("/api/v1/recipes/recommended?diaryId={d}", diaryId), grower)).andReturn());
        after.forEach(item -> assertThat(item.get("recipe").get("id").asText()).isNotEqualTo(matching));

        // Cultivo de outra pessoa não serve de base para sugestões.
        mvc.perform(authed(get("/api/v1/recipes/recommended?diaryId={d}", diaryId), community)).andExpect(status().isNotFound());
        mvc.perform(signedIn(get("/api/v1/recipes/recommended"))).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions rate(Session session, String id, int stars) throws Exception {
        return mvc.perform(authed(put("/api/v1/recipes/{id}/rating", id), session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"stars\":" + stars + "}"));
    }

    private String recipe(Session session, String name, String kind, String visibility, String productId) throws Exception {
        String application = kind.equals("MINERAL") ? "FERTIGATION" : "SOIL_MIX";
        String ingredient = productId == null
                ? "{\"name\":\"Ingrediente base\",\"quantity\":1,\"unit\":\"G\"}"
                : "{\"productId\":\"" + productId + "\",\"quantity\":1,\"unit\":\"ML\"}";
        return body(mvc.perform(authed(post("/api/v1/recipes"), session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","kind":"%s","application":"%s","batchLiters":10,"visibility":"%s",
                                 "ingredients":[%s]}""".formatted(name, kind, application, visibility, ingredient)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private UUID mineralDiary(Session session) throws Exception {
        return UUID.fromString(body(mvc.perform(authed(post("/api/v1/diaries"), session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"Mineral","strainName":"Amnesia","floweringType":"PHOTOPERIOD","seedSex":"FEMINIZED",
                                 "medium":"MINERAL","visibility":"PUBLIC","startedOn":"%s"}""".formatted(LocalDate.now().minusDays(5))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText());
    }

    private String newProduct(Session session) throws Exception {
        String brandId = body(mvc.perform(authed(post("/api/v1/catalog/brands/resolve"), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Marca " + token() + "\",\"confirmNew\":true}"))
                .andReturn()).get("item").get("id").asText();
        return body(mvc.perform(authed(post("/api/v1/catalog/products/resolve"), session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"brandId":"%s","name":"Base %s","category":"NUTRIENT","confirmNew":true}""".formatted(brandId, token())))
                .andReturn()).get("item").get("id").asText();
    }

    private static String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static List<String> ids(JsonNode items) {
        List<String> ids = new ArrayList<>();
        items.forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }
}
