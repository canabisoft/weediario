package br.com.weediario;

import br.com.weediario.catalog.application.usecase.ImportDatasetUseCase;
import br.com.weediario.catalog.application.usecase.ImportDatasetUseCase.DatasetStrain;
import br.com.weediario.catalog.application.usecase.ImportDatasetUseCase.Report;
import br.com.weediario.catalog.domain.model.StrainTraits;
import br.com.weediario.catalog.infrastructure.dataset.DatasetCsvReaderTest;
import br.com.weediario.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogIntegrationTest extends IntegrationTestSupport {

    @Autowired
    ImportDatasetUseCase importDataset;

    @BeforeEach
    void importSample() throws Exception {
        importDataset.importStrains(DatasetCsvReaderTest.sample());
    }

    @Test
    void importIsIdempotentAndSkipsShopCategoriesAsBreeders() throws Exception {
        Report again = importDataset.importStrains(DatasetCsvReaderTest.sample());

        assertThat(again.strainsCreated()).isZero();
        assertThat(again.breedersCreated()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from breeders where name ilike '%sale items%'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select source from strains where name = 'Bubba Kush'", String.class))
                .isEqualTo("DATASET");
    }

    @Test
    void importCleansShopNoiseAndSkipsWhatIsNotAGenetic() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        long strainsBefore = jdbc.queryForObject("select count(*) from strains", Long.class);
        Report report = importDataset.importStrains(List.of(
                // Anúncio de loja: vira a strain que já existe (sem duplicar).
                row("SALE - Bubba Kush - Teste Genetics Seeds", null),
                // Brinde, pacote e preço: não são genéticas.
                row("3 Free Seeds - £75 to £100", null),
                row("Indoor Mixed Pack", "Teste Genetics Seeds"),
                row("Petro $ OG", "Teste Genetics Seeds"),
                // "Banco - Strain" sem breeder: separa e cria o breeder certo.
                row("Quintal " + suffix + " Seeds - Runtz " + suffix, null)));

        assertThat(report.skipped()).isEqualTo(3);
        assertThat(report.strainsCreated()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from strains", Long.class)).isEqualTo(strainsBefore + 1);
        assertThat(jdbc.queryForObject("select count(*) from strains where name ~* '^sale|[$£€]|free seed|mixed pack'",
                Long.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select b.name from strains s join breeders b on b.id = s.breeder_id where s.name = ?""",
                String.class, "Runtz " + suffix)).isEqualTo("Quintal " + suffix + " Seeds");
    }

    @Test
    void moderationActionsOnTheCatalogAreAudited() throws Exception {
        Session grower = newGrower();
        Session moderator = moderator();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String toVerify = body(mvc.perform(authed(post("/api/v1/catalog/breeders/resolve"), grower)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Auditado %s Seeds\",\"confirmNew\":true}".formatted(suffix)))
                .andReturn()).get("item").get("id").asText();
        String toReject = body(mvc.perform(authed(post("/api/v1/catalog/breeders/resolve"), grower)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Errado %s Genetics\",\"confirmNew\":true}".formatted(suffix)))
                .andReturn()).get("item").get("id").asText();

        mvc.perform(authed(post("/api/v1/catalog/breeders/{id}/verify", toVerify), moderator))
                .andExpect(status().isNoContent());
        mvc.perform(authed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/catalog/breeders/{id}", toReject), moderator))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForList("""
                select a.action || ':' || a.item_name || ':' || u.username from catalog_audit a join users u on u.id = a.moderator_id
                 where a.item_id in (?::uuid, ?::uuid) order by a.action""", String.class, toVerify, toReject))
                .containsExactly("REJECTED:Errado " + suffix + " Genetics:" + moderator.username(),
                        "VERIFIED:Auditado " + suffix + " Seeds:" + moderator.username());
    }

    private static DatasetStrain row(String name, String breeder) {
        return new DatasetStrain(name, breeder, StrainTraits.EMPTY);
    }

    @Test
    void autocompleteFindsByPrefixAndTypo() throws Exception {
        mvc.perform(get("/api/v1/catalog/strains/suggest?q=bubb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Bubba Kush"))
                .andExpect(jsonPath("$[0].parentName").value("Teste Genetics Seeds"));
        // Erro de digitação ainda encontra (outro teste pode ter cadastrado "Buba Kush" de propósito).
        JsonNode typo = body(mvc.perform(get("/api/v1/catalog/strains/suggest?q=bubah kush")).andReturn());
        assertThat(typo).anyMatch(item -> item.get("name").asText().equals("Bubba Kush"));
        mvc.perform(get("/api/v1/catalog/strains/suggest?q=b")).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void resolveNeverDuplicates() throws Exception {
        Session grower = newGrower();
        String breederId = breederId("teste genetics");

        // Mesmo nome com outra grafia: é o existente.
        JsonNode exact = resolveStrain(grower, "BUBBA-KUSH", breederId, false);
        assertThat(exact.get("outcome").asText()).isEqualTo("EXISTING");

        // Erro de digitação: "você quis dizer?".
        JsonNode typo = resolveStrain(grower, "Buba Kush", breederId, false);
        assertThat(typo.get("outcome").asText()).isEqualTo("SIMILAR");
        assertThat(typo.get("candidates").get(0).get("name").asText()).isEqualTo("Bubba Kush");

        // Grower confirma que é outra genética: cadastra como item da comunidade.
        JsonNode created = resolveStrain(grower, "Buba Kush", breederId, true);
        assertThat(created.get("outcome").asText()).isEqualTo("CREATED");
        assertThat(created.get("item").get("source").asText()).isEqualTo("COMMUNITY");
        assertThat(resolveStrain(grower, "buba kush", breederId, false).get("outcome").asText()).isEqualTo("EXISTING");
    }

    @Test
    void diaryLinksToCatalogWhetherPickedOrTyped() throws Exception {
        Session grower = newGrower();
        String strainId = mvc.perform(get("/api/v1/catalog/strains/suggest?q=lemon tester")).andReturn()
                .getResponse().getContentAsString().replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");

        String picked = createDiaryJson(grower, "{\"strainId\":\"" + strainId + "\"}");
        String typed = createDiaryJson(grower, "{\"strainName\":\"lemon-tester AUTO\",\"breeder\":\"Teste Genetics\"}");

        mvc.perform(get("/api/v1/diaries/{id}", picked)).andExpect(jsonPath("$.strainId").value(strainId))
                .andExpect(jsonPath("$.strainName").value("Lemon Tester Auto"));
        mvc.perform(get("/api/v1/diaries/{id}", typed)).andExpect(jsonPath("$.strainId").value(strainId));
        mvc.perform(get("/api/v1/catalog/strains/{id}", strainId))
                .andExpect(jsonPath("$.diaries").value(2))
                .andExpect(jsonPath("$.flavors[0]").value("limão"));
    }

    @Test
    void communityFillsGapsButCannotOverwrite() throws Exception {
        Session grower = newGrower();
        String bubba = strainIdByName("Bubba Kush");

        mvc.perform(authed(patch("/api/v1/catalog/strains/{id}", bubba), grower).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"genetics":"Bubble Gum x OG Kush","dominance":"SATIVA"}"""))
                .andExpect(jsonPath("$.changed").value(true));

        mvc.perform(get("/api/v1/catalog/strains/{id}", bubba))
                .andExpect(jsonPath("$.genetics").value("Bubble Gum x OG Kush"))
                .andExpect(jsonPath("$.dominance").value("INDICA_DOMINANT"));

        mvc.perform(authed(patch("/api/v1/catalog/strains/{id}", bubba), grower).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"genetics":"Outra coisa"}"""))
                .andExpect(jsonPath("$.changed").value(false));
    }

    @Test
    void onlyModeratorsVerifyAndMerge() throws Exception {
        Session grower = newGrower();
        Session moderator = moderator();
        String target = strainIdByName("Bubba Kush");
        String duplicate = resolveStrain(grower, "Bubba Kush Duplicada", null, true).get("item").get("id").asText();
        String diary = createDiaryJson(grower, "{\"strainId\":\"" + duplicate + "\"}");

        mvc.perform(authed(post("/api/v1/catalog/strains/{id}/verify", target), grower)).andExpect(status().isForbidden());
        mvc.perform(authed(post("/api/v1/catalog/strains/{id}/verify", target), moderator)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/catalog/strains/{id}", target)).andExpect(jsonPath("$.status").value("VERIFIED"));

        mvc.perform(authed(post("/api/v1/catalog/strains/{id}/merge", duplicate), moderator)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetId\":\"" + target + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/diaries/{id}", diary)).andExpect(jsonPath("$.strainId").value(target));
        mvc.perform(get("/api/v1/catalog/strains/{id}", duplicate)).andExpect(status().isNotFound());
        // O nome da duplicada virou apelido: digitar de novo cai na principal.
        JsonNode again = resolveStrain(grower, "bubba kush duplicada", null, false);
        assertThat(again.get("outcome").asText()).isEqualTo("EXISTING");
        assertThat(again.get("item").get("id").asText()).isEqualTo(target);
    }

    @Test
    void productsFromSetupFeedBrandAndProductRankings() throws Exception {
        Session grower = newGrower();
        String suffix = UUID.randomUUID().toString().substring(0, 5);
        String brandId = body(mvc.perform(authed(post("/api/v1/catalog/brands/resolve"), grower)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Marca " + suffix + "\"}"))
                .andReturn()).get("item").get("id").asText();
        JsonNode product = body(mvc.perform(authed(post("/api/v1/catalog/products/resolve"), grower)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"brandId":"%s","name":"Bio Grow %s","category":"NUTRIENT"}""".formatted(brandId, suffix)))
                .andReturn());
        String productId = product.get("item").get("id").asText();
        assertThat(product.get("outcome").asText()).isEqualTo("CREATED");

        String setupId = body(mvc.perform(authed(post("/api/v1/me/setups"), grower).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Tenda","environment":"INDOOR","medium":"LIVING_SOIL","productIds":["%s"]}"""
                                .formatted(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.products[0].brand").value("Marca " + suffix))
                .andReturn()).get("id").asText();
        String diaryId = createDiaryJson(grower, "{\"strainName\":\"Bubba Kush\",\"breeder\":\"Teste Genetics Seeds\","
                + "\"setupId\":\"" + setupId + "\"}");
        mvc.perform(get("/api/v1/diaries/{id}", diaryId)).andExpect(jsonPath("$.setup.products[0].name").value("Bio Grow " + suffix));

        JsonNode products = body(mvc.perform(signedIn(get("/api/v1/catalog/rankings/PRODUCTS?category=NUTRIENT&size=50")))
                .andExpect(status().isOk()).andReturn());
        assertThat(products.get("items")).anyMatch(row -> row.get("id").asText().equals(productId)
                && row.get("diaries").asInt() == 1 && row.get("parentName").asText().equals("Marca " + suffix));
        JsonNode brands = body(mvc.perform(signedIn(get("/api/v1/catalog/rankings/BRANDS?size=50"))).andReturn());
        assertThat(brands.get("items")).anyMatch(row -> row.get("id").asText().equals(brandId));
        JsonNode strains = body(mvc.perform(signedIn(get("/api/v1/catalog/rankings/STRAINS?size=50"))).andReturn());
        assertThat(strains.get("items")).anyMatch(row -> row.get("name").asText().equals("Bubba Kush"));
    }

    @Test
    void catalogListingIncludesStrainsWithoutReviews() throws Exception {
        mvc.perform(get("/api/v1/catalog/strains?q=lemon tester"))
                .andExpect(jsonPath("$.items[0].name").value("Lemon Tester Auto"))
                .andExpect(jsonPath("$.items[0].reviewCount").value(0));
        mvc.perform(get("/api/v1/strains/{id}/reviews", strainIdByName("Lemon Tester Auto")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/strains/{id}", strainIdByName("Lemon Tester Auto")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount").value(0));
        mvc.perform(get("/api/v1/strains/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    // --------------------------------------------------------------------------------- helpers

    private JsonNode resolveStrain(Session session, String name, String breederId, boolean confirmNew) throws Exception {
        String breeder = breederId == null ? "null" : "\"" + breederId + "\"";
        return body(mvc.perform(authed(post("/api/v1/catalog/strains/resolve"), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"breederId\":%s,\"confirmNew\":%s}".formatted(name, breeder, confirmNew)))
                .andExpect(status().isOk()).andReturn());
    }

    private String createDiaryJson(Session session, String strainFields) throws Exception {
        String base = """
                {"title":"Catálogo","floweringType":"PHOTOPERIOD","seedSex":"FEMINIZED","medium":"LIVING_SOIL","startedOn":"%s",
                """.formatted(LocalDate.now());
        MvcResult result = mvc.perform(authed(post("/api/v1/diaries"), session).contentType(MediaType.APPLICATION_JSON)
                        .content(base + strainFields.substring(1)))
                .andExpect(status().isCreated()).andReturn();
        return body(result).get("id").asText();
    }

    private String breederId(String query) throws Exception {
        return body(mvc.perform(get("/api/v1/catalog/breeders/suggest?q=" + query)).andReturn()).get(0).get("id").asText();
    }

    private String strainIdByName(String name) {
        return jdbc.queryForObject("select id::text from strains where name = ? order by created_at limit 1", String.class, name);
    }
}
