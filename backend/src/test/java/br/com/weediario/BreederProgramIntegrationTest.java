package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Programa de breeders (sementes em teste até a comunidade validar), nota de produtos na review e banco aberto.
 */
class BreederProgramIntegrationTest extends IntegrationTestSupport {

    private static final String REVIEW = """
            {"vigor":4,"pestResistance":4,"easeOfGrowth":5,"yield":4,"terpeneProfile":5,%s
             "report":"Cultivo tranquilo, flora de 9 semanas e aroma cítrico bem marcado."}""";

    @Test
    void breederSeedBecomesOfficialAfterThreeOutsideGrowersAndAReview() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Session rep = newGrower();
        Session moderator = moderator();
        String breederId = breeder(rep, "Banco Programa " + suffix);

        // 1. Pedido de representação: ainda não dá para cadastrar sementes.
        requestRepresentation(rep, breederId).andExpect(status().isCreated());
        requestRepresentation(rep, breederId).andExpect(status().isUnprocessableEntity());
        registerStrain(rep, breederId, "Nova Kush " + suffix).andExpect(status().isUnprocessableEntity());
        mvc.perform(authed(get("/api/v1/breeder-program"), rep))
                .andExpect(jsonPath("$.representations[0].status").value("PENDING"));

        // 2. Moderação aprova (só moderador vê a fila e a nota).
        mvc.perform(authed(get("/api/v1/moderation/breeder-representatives"), rep)).andExpect(status().isForbidden());
        mvc.perform(authed(get("/api/v1/moderation/catalog/counts"), moderator))
                .andExpect(jsonPath("$.REPRESENTATIVES").isNumber());
        JsonNode request = representativeRequest(moderator, rep.username());
        assertThat(request.get("note").asText()).contains("site oficial");
        String repId = request.get("userId").asText();
        mvc.perform(authed(post("/api/v1/moderation/breeder-representatives/{b}/{u}/approve", breederId, repId), rep))
                .andExpect(status().isForbidden());
        mvc.perform(authed(post("/api/v1/moderation/breeder-representatives/{b}/{u}/approve", breederId, repId), moderator))
                .andExpect(status().isNoContent());

        // 3. Cadastro da semente: entra em teste, com filtro anti-comércio e sem duplicar.
        String strainName = "Nova Kush " + suffix;
        JsonNode created = body(registerStrain(rep, breederId, strainName).andExpect(status().isCreated()).andReturn());
        assertThat(created.get("status").asText()).isEqualTo("TESTING");
        assertThat(created.get("source").asText()).isEqualTo("BREEDER");
        String strainId = created.get("id").asText();
        registerStrain(rep, breederId, strainName.toUpperCase())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Complete as informações")));
        registerStrain(rep, breederId, "Vendo sementes R$ 50").andExpect(status().isUnprocessableEntity());

        // 4. Em teste: fora da listagem oficial, do banco, da busca de avaliadas e do banco aberto.
        assertThat(ids(get("/api/v1/catalog/strains?size=100&q=" + strainName), "items")).doesNotContain(strainId);
        assertThat(ids(get("/api/v1/catalog/breeders/{id}/strains", breederId), "items")).doesNotContain(strainId);
        assertThat(ids(get("/api/v1/strains?size=100&query=" + strainName), "items")).doesNotContain(strainId);
        assertThat(ids(get("/api/v1/open-data/strains"), "strains")).doesNotContain(strainId);
        assertThat(ids(get("/api/v1/catalog/strains/testing?breederId=" + breederId), "items")).contains(strainId);
        mvc.perform(get("/api/v1/catalog/strains/suggest?q=" + strainName))
                .andExpect(jsonPath("$[0].id").value(strainId))
                .andExpect(jsonPath("$[0].status").value("TESTING"));
        mvc.perform(authed(post("/api/v1/catalog/strains/{id}/verify", strainId), moderator))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Não dá para verificar")));

        // 5. Três cultivadores de fora cultivam; um conclui e avalia, com nota a um produto do cultivo (diário
        //    privado também conta). O representante do breeder não entra na contagem de testadores.
        Session first = newGrower();
        Session second = newGrower();
        Session third = newGrower();
        diary(first, strainId, "PUBLIC");
        diary(second, strainId, "PUBLIC");
        String productId = product(third);
        UUID thirdDiary = diary(third, strainId, "PRIVATE");
        mvc.perform(authed(put("/api/v1/diaries/{id}/products", thirdDiary), third)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productIds\":[\"%s\"]}".formatted(productId)))
                .andExpect(status().isOk());
        complete(third, thirdDiary);
        review(third, thirdDiary, "\"products\":[{\"productId\":\"%s\",\"rating\":5}],".formatted(UUID.randomUUID()))
                .andExpect(status().isUnprocessableEntity());
        review(third, thirdDiary, "\"products\":[{\"productId\":\"%s\",\"rating\":4}],".formatted(productId))
                .andExpect(status().isCreated());

        // Mesmo com 3 cultivadores e 1 avaliação de fora, não entra: falta a colheita e a avaliação do próprio breeder.
        mvc.perform(get("/api/v1/catalog/strains/{id}", strainId))
                .andExpect(jsonPath("$.status").value("TESTING"))
                .andExpect(jsonPath("$.testing.testers").value(3))
                .andExpect(jsonPath("$.testing.testersRequired").value(3))
                .andExpect(jsonPath("$.testing.reviews").value(1))
                .andExpect(jsonPath("$.testing.breederReviewed").value(false));

        // 6. O breeder colhe e publica a primeira avaliação do que esperar: a review dele não conta como avaliação de
        //    fora, mas libera a entrada no banco oficial.
        UUID repDiary = diary(rep, strainId, "PUBLIC");
        complete(rep, repDiary);
        review(rep, repDiary, "").andExpect(status().isCreated());

        JsonNode promoted = body(mvc.perform(get("/api/v1/catalog/strains/{id}", strainId)).andReturn());
        assertThat(promoted.get("status").asText()).isEqualTo("VERIFIED");
        assertThat(promoted.hasNonNull("promotedAt")).isTrue();
        assertThat(promoted.hasNonNull("testing")).as("andamento só enquanto está em teste").isFalse();
        assertThat(jdbc.queryForObject("select promoted_at is not null from strains where id = ?::uuid", Boolean.class,
                strainId)).isTrue();
        assertThat(ids(get("/api/v1/catalog/strains/testing?breederId=" + breederId), "items")).doesNotContain(strainId);
        assertThat(ids(get("/api/v1/catalog/breeders/{id}/strains", breederId), "items")).contains(strainId);
        mvc.perform(authed(get("/api/v1/breeder-program"), rep))
                .andExpect(jsonPath("$.strains[0].id").value(strainId))
                .andExpect(jsonPath("$.strains[0].status").value("VERIFIED"));

        // Nota do produto entra na média; a avaliação não aponta para o diário privado nem para o autor.
        mvc.perform(get("/api/v1/catalog/products/{id}", productId))
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.rating").value(4.0));
        JsonNode productReviews = body(mvc.perform(get("/api/v1/catalog/products/{id}/reviews", productId))
                .andExpect(status().isOk()).andReturn());
        JsonNode productReview = productReviews.get("items").get(0);
        assertThat(productReview.get("rating").asInt()).isEqualTo(4);
        assertThat(productReview.get("strainId").asText()).isEqualTo(strainId);
        assertThat(productReview.hasNonNull("diaryId")).isFalse();
        assertThat(productReviews.toString()).doesNotContain(third.username());

        // 7. Banco aberto: agora a semente está lá, sem nenhum dado pessoal.
        MvcResult open = mvc.perform(get("/api/v1/open-data/strains"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("max-age=3600")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("public")))
                .andExpect(jsonPath("$.license").value("ODbL-1.0"))
                .andReturn();
        JsonNode dump = body(open);
        JsonNode row = null;
        for (JsonNode strain : dump.get("strains")) {
            if (strain.get("id").asText().equals(strainId)) {
                row = strain;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.get("status").asText()).isEqualTo("VERIFIED");
        assertThat(row.get("source").asText()).isEqualTo("BREEDER");
        assertThat(row.get("reviewCount").asInt()).isEqualTo(2);
        assertThat(row.get("publicDiaries").asInt()).isEqualTo(3);
        assertThat(row.hasNonNull("promotedAt")).isTrue();
        String raw = open.getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (Session person : new Session[]{rep, first, second, third}) {
            assertThat(raw).doesNotContain(person.username());
        }
        assertThat(raw).doesNotContain(thirdDiary.toString()).doesNotContain(repDiary.toString());

        String csv = mvc.perform(get("/api/v1/open-data/strains.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("max-age=3600")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("id,name,breeder,").contains(strainName).doesNotContain(rep.username())
                .doesNotContain(third.username());
    }

    @Test
    void revokedRepresentativeCannotRegisterAndUnusedTestingSeedCanBeRejected() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Session rep = newGrower();
        Session moderator = moderator();
        String breederId = breeder(rep, "Banco Revogado " + suffix);
        requestRepresentation(rep, breederId).andExpect(status().isCreated());
        String repId = representativeRequest(moderator, rep.username()).get("userId").asText();
        mvc.perform(authed(post("/api/v1/moderation/breeder-representatives/{b}/{u}/approve", breederId, repId), moderator))
                .andExpect(status().isNoContent());
        String strainId = body(registerStrain(rep, breederId, "Spam Haze " + suffix).andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        // Moderador recusa a semente (ninguém usa) e revoga o representante.
        mvc.perform(authed(delete("/api/v1/catalog/strains/{id}", strainId), moderator)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/catalog/strains/{id}", strainId)).andExpect(status().isNotFound());
        mvc.perform(authed(delete("/api/v1/moderation/breeder-representatives/{b}/{u}", breederId, repId), moderator))
                .andExpect(status().isNoContent());
        registerStrain(rep, breederId, "Outra Haze " + suffix).andExpect(status().isUnprocessableEntity());
        mvc.perform(authed(get("/api/v1/breeder-program"), rep)).andExpect(jsonPath("$.representations").isEmpty());
    }

    @Test
    void openDataIsPublicAndCsvIsSafeForSpreadsheets() throws Exception {
        Session grower = newGrower();
        String name = "=HYPERLINK Kush " + UUID.randomUUID().toString().substring(0, 6);
        mvc.perform(authed(post("/api/v1/catalog/strains/resolve"), grower).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"confirmNew\":true}".formatted(name)))
                .andExpect(status().isOk());

        String csv = mvc.perform(get("/api/v1/open-data/strains.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.startsWith("text/csv")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("\"'" + name + "\"").doesNotContain(",\"" + name);
        mvc.perform(post("/api/v1/open-data/strains")).andExpect(status().isUnauthorized());
    }

    @Test
    void independentBreederJoinsTheProgramAndMayChooseToBeCredited() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Session creator = newGrower();
        Session moderator = moderator();
        // Breeder independente que ainda não estava no catálogo: entra pelo "achar ou cadastrar".
        String breederId = breeder(creator, "Quintal Genetics " + suffix);

        // Crédito público só para independente.
        mvc.perform(authed(post("/api/v1/breeder-program/representations"), creator)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"breederId":"%s","note":"Meus diários de polinização estão no meu perfil.",
                                 "kind":"SEEDBANK","publicCredit":true}""".formatted(breederId)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(authed(post("/api/v1/breeder-program/representations"), creator)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"breederId":"%s","note":"Meus diários de polinização estão no meu perfil.",
                                 "kind":"INDEPENDENT","publicCredit":true}""".formatted(breederId)))
                .andExpect(status().isCreated());

        JsonNode request = representativeRequest(moderator, creator.username());
        assertThat(request.get("claimedKind").asText()).isEqualTo("INDEPENDENT");
        assertThat(request.get("publicCredit").asBoolean()).isTrue();
        // Antes da aprovação, ninguém aparece como criador.
        mvc.perform(get("/api/v1/catalog/breeders/{id}", breederId))
                .andExpect(jsonPath("$.creators").isEmpty());

        mvc.perform(authed(post("/api/v1/moderation/breeder-representatives/{b}/{u}/approve", breederId,
                        request.get("userId").asText()), moderator))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/catalog/breeders/{id}", breederId))
                .andExpect(jsonPath("$.kind").value("INDEPENDENT"))
                .andExpect(jsonPath("$.creators[0]").value(creator.username()));

        // Semente com geração informada.
        String strainId = body(mvc.perform(authed(post("/api/v1/breeder-program/strains"), creator)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"breederId":"%s","name":"Quintal Haze %s",
                                 "traits":{"genetics":"Haze x Skunk","generation":"F2"}}""".formatted(breederId, suffix)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        mvc.perform(get("/api/v1/catalog/strains/{id}", strainId))
                .andExpect(jsonPath("$.generation").value("F2"))
                .andExpect(jsonPath("$.breederKind").value("INDEPENDENT"));

        // A pessoa pode voltar ao anonimato quando quiser.
        mvc.perform(authed(put("/api/v1/breeder-program/representations/{b}/credit", breederId), creator)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"publicCredit\":false}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/catalog/breeders/{id}", breederId)).andExpect(jsonPath("$.creators").isEmpty());

        // O banco aberto ganha as colunas novas no fim (sem mudar a ordem das antigas).
        String header = mvc.perform(get("/api/v1/open-data/strains.csv")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8).lines().findFirst().orElseThrow();
        assertThat(header).endsWith("avg_grams_per_plant,breeder_kind,generation");
    }

    // ------------------------------------------------------------------------------------- helpers

    private String breeder(Session session, String name) throws Exception {
        return body(mvc.perform(authed(post("/api/v1/catalog/breeders/resolve"), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"confirmNew\":true}".formatted(name)))
                .andExpect(status().isOk()).andReturn()).get("item").get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions requestRepresentation(Session session, String breederId)
            throws Exception {
        return mvc.perform(authed(post("/api/v1/breeder-program/representations"), session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"breederId\":\"%s\",\"note\":\"Sou do banco, confira o site oficial.\"}".formatted(breederId)));
    }

    private org.springframework.test.web.servlet.ResultActions registerStrain(Session session, String breederId,
                                                                           String name) throws Exception {
        return mvc.perform(authed(post("/api/v1/breeder-program/strains"), session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"breederId":"%s","name":"%s",
                         "traits":{"floweringType":"AUTOFLOWER","flavors":["cítrico"],"genetics":"Kush x Haze"}}"""
                        .formatted(breederId, name)));
    }

    private JsonNode representativeRequest(Session moderator, String username) throws Exception {
        JsonNode page = body(mvc.perform(authed(get("/api/v1/moderation/breeder-representatives?size=100"), moderator))
                .andExpect(status().isOk()).andReturn());
        for (JsonNode item : page.get("items")) {
            if (item.get("username").asText().equals(username)) {
                return item;
            }
        }
        throw new AssertionError("Pedido de " + username + " não está na fila");
    }

    private UUID diary(Session session, String strainId, String visibility) throws Exception {
        MvcResult result = mvc.perform(authed(post("/api/v1/diaries"), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Teste da semente","strainId":"%s","floweringType":"AUTOFLOWER",
                                 "seedSex":"FEMINIZED","medium":"LIVING_SOIL","visibility":"%s","startedOn":"%s"}"""
                                .formatted(strainId, visibility, LocalDate.now().minusDays(80))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(body(result).get("id").asText());
    }

    private void complete(Session session, UUID diaryId) throws Exception {
        mvc.perform(authed(post("/api/v1/diaries/{id}/complete", diaryId), session)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions review(Session session, UUID diaryId, String extra)
            throws Exception {
        return mvc.perform(authed(post("/api/v1/diaries/{id}/review", diaryId), session)
                .contentType(MediaType.APPLICATION_JSON).content(REVIEW.formatted(extra)));
    }

    private String product(Session session) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String brandId = body(mvc.perform(authed(post("/api/v1/catalog/brands/resolve"), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Marca Nota %s\",\"confirmNew\":true}".formatted(suffix)))
                .andReturn()).get("item").get("id").asText();
        return body(mvc.perform(authed(post("/api/v1/catalog/products/resolve"), session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"brandId":"%s","name":"Bloom %s","category":"NUTRIENT","confirmNew":true}"""
                                .formatted(brandId, suffix)))
                .andReturn()).get("item").get("id").asText();
    }

    private java.util.List<String> ids(org.springframework.test.web.servlet.RequestBuilder request, String field)
            throws Exception {
        JsonNode node = body(mvc.perform(request).andExpect(status().isOk()).andReturn()).get(field);
        java.util.List<String> ids = new java.util.ArrayList<>();
        node.forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }
}
