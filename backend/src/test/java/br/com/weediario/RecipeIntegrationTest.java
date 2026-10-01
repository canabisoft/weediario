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

class RecipeIntegrationTest extends IntegrationTestSupport {

    @Test
    void mineralRecipeMixesBrandedProductsAndGenericSaltsAndCountsInProducts() throws Exception {
        Session grower = newGrower();
        String productId = newProduct(grower, "Flora Micro");

        // Nome/marca enviados para o produto de marca são ignorados: vêm do catálogo.
        String id = body(mvc.perform(authed(post("/api/v1/recipes"), grower).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Flora semana 3","kind":"MINERAL","application":"FERTIGATION","stage":"FLOWERING",
                                 "batchLiters":10,"targetEc":1.8,"targetPh":5.8,"visibility":"PUBLIC",
                                 "notes":"Misturar na ordem da lista.",
                                 "ingredients":[
                                   {"productId":"%s","name":"qualquer coisa","quantity":15,"unit":"ML"},
                                   {"name":"Nitrato de cálcio","quantity":8.5,"unit":"G"},
                                   {"name":"Sulfato de magnésio","brand":"Genérico","quantity":5,"unit":"G"}]}"""
                                .formatted(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ingredients[0].name").value("Flora Micro"))
                .andExpect(jsonPath("$.ingredients[0].brand").value(brandOf(productId)))
                .andExpect(jsonPath("$.ingredients[1].productId").doesNotExist())
                .andExpect(jsonPath("$.ingredients[2].brand").value("Genérico"))
                .andReturn()).get("id").asText();

        // Visitante anônimo vê a receita pública e ela aparece no perfil do autor e na busca por produto.
        mvc.perform(get("/api/v1/recipes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipe.author").value(grower.username()))
                .andExpect(jsonPath("$.recipe.targetEc").value(1.8))
                .andExpect(jsonPath("$.ownedByViewer").value(false));
        assertThat(ids(body(mvc.perform(get("/api/v1/growers/{u}/recipes", grower.username())).andReturn())))
                .containsExactly(id);
        assertThat(ids(body(mvc.perform(get("/api/v1/recipes?productId={p}&kind=MINERAL", productId)).andReturn())))
                .containsExactly(id);
        assertThat(ids(body(mvc.perform(get("/api/v1/recipes?kind=ORGANIC&productId={p}", productId)).andReturn())))
                .isEmpty();
        assertThat(productRow(productId).get("recipes").asInt()).isEqualTo(1);
        assertThat(body(mvc.perform(get("/api/v1/recipes/ingredients/suggest?q=nitrato")).andReturn()).toString())
                .contains("Nitrato de cálcio");
    }

    @Test
    void privateRecipeIsInvisibleToOthersAndDoesNotCount() throws Exception {
        Session owner = newGrower();
        Session stranger = newGrower();
        String productId = newProduct(owner, "Bloom");
        String id = create(owner, "Segredo", "PRIVATE", productId);

        mvc.perform(get("/api/v1/recipes/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(authed(get("/api/v1/recipes/{id}", id), stranger)).andExpect(status().isNotFound());
        mvc.perform(authed(get("/api/v1/recipes/{id}", id), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ownedByViewer").value(true));
        mvc.perform(authed(post("/api/v1/recipes/{id}/replicate", id), stranger)).andExpect(status().isNotFound());
        assertThat(ids(body(mvc.perform(get("/api/v1/growers/{u}/recipes", owner.username())).andReturn()))).isEmpty();
        assertThat(ids(body(mvc.perform(authed(get("/api/v1/me/recipes"), owner)).andReturn()))).containsExactly(id);
        assertThat(productRow(productId).get("recipes").asInt()).isZero();
        // Terceiros também não editam nem apagam.
        mvc.perform(authed(delete("/api/v1/recipes/{id}", id), stranger)).andExpect(status().isNotFound());
    }

    @Test
    void replicatingCopiesTheRecipeToTheOtherGrowerWithCredit() throws Exception {
        Session author = newGrower();
        Session fan = newGrower();
        String original = create(author, "Super solo 100 L", "PUBLIC", null);

        mvc.perform(authed(post("/api/v1/recipes/{id}/replicate", original), author)).andExpect(status().isUnprocessableEntity());
        String copy = body(mvc.perform(authed(post("/api/v1/recipes/{id}/replicate", original), fan))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author").value(fan.username()))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.replicatedFrom.author").value(author.username()))
                .andExpect(jsonPath("$.ingredients[0].name").value("Húmus de minhoca"))
                .andReturn()).get("id").asText();
        assertThat(copy).isNotEqualTo(original);
        mvc.perform(get("/api/v1/recipes/{id}", original)).andExpect(jsonPath("$.recipe.replications").value(1));

        // A cópia é de quem replicou: pode ajustar e publicar no próprio perfil.
        mvc.perform(authed(put("/api/v1/recipes/{id}", copy), fan).contentType(MediaType.APPLICATION_JSON)
                        .content(organicBody("Meu super solo", "PUBLIC")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Meu super solo"));
        assertThat(ids(body(mvc.perform(get("/api/v1/growers/{u}/recipes", fan.username())).andReturn())))
                .containsExactly(copy);
        mvc.perform(get("/api/v1/recipes/{id}", original)).andExpect(jsonPath("$.recipe.name").value("Super solo 100 L"));
    }

    @Test
    void diaryShowsTheRecipesItUsesRespectingRecipePrivacy() throws Exception {
        Session grower = newGrower();
        Session other = newGrower();
        UUID diaryId = createDiary(grower, "Gelato", "PUBLIC", LocalDate.now().minusDays(15).toString());
        String mine = create(grower, "Minha vega", "PRIVATE", null);
        String theirs = create(other, "Flora pública", "PUBLIC", null);
        String theirsPrivate = create(other, "Flora secreta", "PRIVATE", null);

        mvc.perform(authed(put("/api/v1/diaries/{d}/recipes", diaryId), grower).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeIds\":[\"%s\"]}".formatted(theirsPrivate)))
                .andExpect(status().isNotFound());
        mvc.perform(authed(put("/api/v1/diaries/{d}/recipes", diaryId), other).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeIds\":[\"%s\"]}".formatted(theirs)))
                .andExpect(status().isNotFound());
        mvc.perform(authed(put("/api/v1/diaries/{d}/recipes", diaryId), grower).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeIds\":[\"%s\",\"%s\"]}".formatted(mine, theirs)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Visitante vê só a receita pública; o autor vê as duas.
        assertThat(names(body(mvc.perform(get("/api/v1/diaries/{d}/recipes", diaryId)).andReturn())))
                .containsExactly("Flora pública");
        assertThat(names(body(mvc.perform(authed(get("/api/v1/diaries/{d}/recipes", diaryId), grower)).andReturn())))
                .containsExactlyInAnyOrder("Minha vega", "Flora pública");
        mvc.perform(get("/api/v1/recipes/{id}", theirs)).andExpect(jsonPath("$.recipe.diaries").value(1));
    }

    @Test
    void rejectsInconsistentRecipes() throws Exception {
        Session grower = newGrower();
        mvc.perform(authed(post("/api/v1/recipes"), grower).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Solo com EC","kind":"ORGANIC","application":"SOIL_MIX","batchLiters":100,"targetEc":1.2,
                         "ingredients":[{"name":"Bokashi","quantity":2,"unit":"KG"}]}"""))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(authed(post("/api/v1/recipes"), grower).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Vazia","kind":"MINERAL","application":"FERTIGATION","batchLiters":10,"ingredients":[]}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(authed(post("/api/v1/recipes"), grower).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Repetida","kind":"MINERAL","application":"FERTIGATION","batchLiters":10,
                         "ingredients":[{"name":"MKP","quantity":1,"unit":"G"},{"name":"mkp","quantity":2,"unit":"G"}]}"""))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/recipes").contentType(MediaType.APPLICATION_JSON).content(organicBody("Anônima", "PUBLIC")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mergingADuplicateProductKeepsRecipesPointingToTheMainOne() throws Exception {
        Session grower = newGrower();
        Session moderator = moderator();
        String duplicate = newProduct(grower, "Bio Grow");
        String target = newProduct(grower, "Grow Supremo");
        String id = create(grower, "Com duplicata", "PUBLIC", duplicate);

        mvc.perform(authed(post("/api/v1/catalog/products/{id}/merge", duplicate), moderator)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetId\":\"" + target + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/recipes/{id}", id))
                .andExpect(jsonPath("$.recipe.ingredients[0].productId").value(target))
                .andExpect(jsonPath("$.recipe.ingredients[0].name").value("Bio Grow"));
    }

    private String create(Session session, String name, String visibility, String productId) throws Exception {
        String body = productId == null ? organicBody(name, visibility) : """
                {"name":"%s","kind":"MINERAL","application":"FERTIGATION","batchLiters":10,"visibility":"%s",
                 "ingredients":[{"productId":"%s","quantity":2,"unit":"ML"}]}""".formatted(name, visibility, productId);
        return body(mvc.perform(authed(post("/api/v1/recipes"), session).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private static String organicBody(String name, String visibility) {
        return """
                {"name":"%s","kind":"ORGANIC","application":"SOIL_MIX","stage":"ALL","batchLiters":100,"visibility":"%s",
                 "ingredients":[{"name":"Húmus de minhoca","quantity":30,"unit":"L"},
                                {"name":"Bokashi","quantity":2,"unit":"KG"},
                                {"name":"Melaço","quantity":2,"unit":"TBSP"}]}""".formatted(name, visibility);
    }

    private String newProduct(Session session, String name) throws Exception {
        String brandId = body(mvc.perform(authed(post("/api/v1/catalog/brands/resolve"), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Marca " + UUID.randomUUID().toString().substring(0, 6) + "\",\"confirmNew\":true}"))
                .andReturn()).get("item").get("id").asText();
        return body(mvc.perform(authed(post("/api/v1/catalog/products/resolve"), session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"brandId":"%s","name":"%s","category":"NUTRIENT","confirmNew":true}""".formatted(brandId, name)))
                .andReturn()).get("item").get("id").asText();
    }

    private String brandOf(String productId) {
        return jdbc.queryForObject("select br.name from products p join brands br on br.id = p.brand_id where p.id = ?::uuid",
                String.class, productId);
    }

    private JsonNode productRow(String productId) throws Exception {
        for (JsonNode row : body(mvc.perform(get("/api/v1/catalog/products?size=50")).andReturn()).get("items")) {
            if (row.get("id").asText().equals(productId)) {
                return row;
            }
        }
        throw new AssertionError("Produto não listado: " + productId);
    }

    private static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }

    private static List<String> names(JsonNode list) {
        List<String> names = new ArrayList<>();
        list.forEach(item -> names.add(item.get("name").asText()));
        return names;
    }
}
