package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ModerationIntegrationTest extends IntegrationTestSupport {

    // ---------------------------------------------------------------------------------- comentários

    @Test
    void moderatorReviewsReportedCommentsWithoutSeeingWhoReported() throws Exception {
        Session owner = newGrower();
        Session author = newGrower();
        Session moderator = moderator();
        UUID diaryId = createDiary(owner, "Moderada", "PUBLIC", LocalDate.now().toString());
        String commentId = comment(author, diaryId, "comentário polêmico");
        for (int i = 0; i < 3; i++) {
            report(newGrower(), diaryId, commentId);
        }

        mvc.perform(authed(get("/api/v1/moderation/comments"), owner)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/moderation/comments")).andExpect(status().isUnauthorized());

        JsonNode queued = queuedComment(moderator, commentId);
        assertThat(queued.get("hidden").asBoolean()).isTrue();
        assertThat(queued.get("reports").asInt()).isEqualTo(3);
        assertThat(queued.get("author").asText()).isEqualTo(author.username());
        assertThat(queued.get("diaryTitle").asText()).isEqualTo("Tenda de teste");
        assertThat(queued.fieldNames()).toIterable().doesNotContain("reporter", "reporters");
        mvc.perform(authed(get("/api/v1/moderation/comments/counts"), moderator))
                .andExpect(jsonPath("$.hidden").isNumber());

        // Denúncias improcedentes: volta a aparecer e sai da fila.
        mvc.perform(authed(post("/api/v1/moderation/comments/{id}/keep", commentId), owner))
                .andExpect(status().isForbidden());
        mvc.perform(authed(post("/api/v1/moderation/comments/{id}/keep", commentId), moderator))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/diaries/{id}/comments", diaryId)).andExpect(jsonPath("$.items[0].id").value(commentId));
        assertThat(queuedComment(moderator, commentId)).isNull();

        // Nova denúncia volta para a fila, e a moderação pode remover.
        report(newGrower(), diaryId, commentId);
        assertThat(queuedComment(moderator, commentId).get("reports").asInt()).isEqualTo(1);
        mvc.perform(authed(delete("/api/v1/moderation/comments/{id}", commentId), moderator))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/diaries/{id}/comments", diaryId)).andExpect(jsonPath("$.totalItems").value(0));
    }

    // ------------------------------------------------------------------------------------- catálogo

    @Test
    void mergingBreedersMovesStrainsDiariesAndKeepsTheOldNameAsAlias() throws Exception {
        Session grower = newGrower();
        Session moderator = moderator();
        String suffix = suffix();
        String target = resolve(grower, "breeders", "{\"name\":\"Zetagen " + suffix + "\",\"confirmNew\":true}");
        mvc.perform(authed(post("/api/v1/catalog/breeders/{id}/verify", target), moderator)).andExpect(status().isNoContent());
        String duplicate = resolve(grower, "breeders", "{\"name\":\"Zetagenn " + suffix + "\",\"confirmNew\":true}");

        String sameInTarget = strain(grower, "Lime Pie " + suffix, target);
        String sameInDuplicate = strain(grower, "Lime Pie " + suffix, duplicate);
        String onlyInDuplicate = strain(grower, "Mango Tart " + suffix, duplicate);
        String diary = diary(grower, "{\"strainId\":\"" + sameInDuplicate + "\"}");

        // A fila mostra o cadastro novo com o verificado parecido como candidato a duplicata.
        JsonNode pending = body(mvc.perform(authed(get("/api/v1/moderation/catalog/breeders?size=50"), moderator))
                .andExpect(status().isOk()).andReturn()).get("items");
        JsonNode queued = find(pending, duplicate);
        assertThat(queued).isNotNull();
        assertThat(queued.get("similar").findValuesAsText("id")).contains(target);
        assertThat(find(pending, target)).isNull();
        mvc.perform(authed(get("/api/v1/moderation/catalog/counts"), moderator)).andExpect(jsonPath("$.BREEDERS").isNumber());
        mvc.perform(authed(get("/api/v1/moderation/catalog/breeders"), grower)).andExpect(status().isForbidden());

        mvc.perform(authed(post("/api/v1/catalog/breeders/{id}/merge", duplicate), moderator)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetId\":\"" + target + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target));

        mvc.perform(get("/api/v1/catalog/strains/{id}", sameInDuplicate)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/diaries/{id}", diary))
                .andExpect(jsonPath("$.strainId").value(sameInTarget));
        mvc.perform(get("/api/v1/catalog/strains/{id}", onlyInDuplicate)).andExpect(jsonPath("$.breederId").value(target));
        mvc.perform(get("/api/v1/catalog/breeders/{id}", duplicate)).andExpect(status().isNotFound());

        // Quem digitar o nome antigo cai no principal, e a strain juntada continua achável pelo breeder novo.
        JsonNode again = body(mvc.perform(authed(post("/api/v1/catalog/breeders/resolve"), grower)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Zetagenn " + suffix + "\"}"))
                .andReturn());
        assertThat(again.get("outcome").asText()).isEqualTo("EXISTING");
        assertThat(again.get("item").get("id").asText()).isEqualTo(target);
        assertThat(strain(grower, "Lime Pie " + suffix, target)).isEqualTo(sameInTarget);
    }

    @Test
    void mergingBrandsJoinsProductsWithTheSameName() throws Exception {
        Session grower = newGrower();
        Session moderator = moderator();
        String suffix = suffix();
        String target = resolve(grower, "brands", "{\"name\":\"Nutrix " + suffix + "\",\"confirmNew\":true}");
        String duplicate = resolve(grower, "brands", "{\"name\":\"Nutrixx " + suffix + "\",\"confirmNew\":true}");
        String productInTarget = product(grower, target, "Grow A");
        String productInDuplicate = product(grower, duplicate, "Grow A");
        String onlyInDuplicate = product(grower, duplicate, "Bloom B");

        String setup = body(mvc.perform(authed(post("/api/v1/me/setups"), grower)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Com produtos","environment":"INDOOR","productIds":["%s","%s"]}"""
                                .formatted(productInDuplicate, onlyInDuplicate)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        diary(grower, "{\"strainName\":\"Brand Test\",\"setupId\":\"" + setup + "\"}");

        mvc.perform(authed(post("/api/v1/catalog/brands/{id}/merge", duplicate), moderator)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetId\":\"" + target + "\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from products where id = ?::uuid", Long.class, productInDuplicate))
                .isZero();
        assertThat(jdbc.queryForObject("select brand_id::text from products where id = ?::uuid", String.class, onlyInDuplicate))
                .isEqualTo(target);
        assertThat(jdbc.queryForList("select product_id::text from grow_setup_products where setup_id = ?::uuid order by position",
                String.class, setup)).containsExactly(productInTarget, onlyInDuplicate);
        assertThat(jdbc.queryForObject("select count(*) from diary_products where product_id = ?::uuid", Long.class,
                productInTarget)).isEqualTo(1);
        // Nome antigo da marca e do produto continuam levando ao principal.
        assertThat(resolve(grower, "brands", "{\"name\":\"Nutrixx " + suffix + "\"}")).isEqualTo(target);
    }

    @Test
    void onlyUnusedItemsCanBeRejected() throws Exception {
        Session grower = newGrower();
        Session moderator = moderator();
        String suffix = suffix();
        String junk = resolve(grower, "breeders", "{\"name\":\"Asdfgh " + suffix + "\",\"confirmNew\":true}");
        String used = resolve(grower, "breeders", "{\"name\":\"Usado " + suffix + "\",\"confirmNew\":true}");
        strain(grower, "Qualquer " + suffix, used);

        mvc.perform(authed(delete("/api/v1/catalog/breeders/{id}", junk), grower)).andExpect(status().isForbidden());
        mvc.perform(authed(delete("/api/v1/catalog/breeders/{id}", junk), moderator)).andExpect(status().isNoContent());
        mvc.perform(authed(delete("/api/v1/catalog/breeders/{id}", junk), moderator)).andExpect(status().isNotFound());
        mvc.perform(authed(delete("/api/v1/catalog/breeders/{id}", used), moderator))
                .andExpect(status().isUnprocessableEntity());
    }

    // --------------------------------------------------------------------------------------- helpers

    private JsonNode queuedComment(Session moderator, String commentId) throws Exception {
        JsonNode items = body(mvc.perform(authed(get("/api/v1/moderation/comments?size=100"), moderator))
                .andExpect(status().isOk()).andReturn()).get("items");
        return find(items, commentId);
    }

    private static JsonNode find(JsonNode items, String id) {
        for (JsonNode item : items) {
            if (item.get("id").asText().equals(id)) {
                return item;
            }
        }
        return null;
    }

    private String comment(Session author, UUID diaryId, String text) throws Exception {
        return body(mvc.perform(authed(post("/api/v1/diaries/{id}/comments", diaryId), author)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + text + "\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private void report(Session reporter, UUID diaryId, String commentId) throws Exception {
        mvc.perform(authed(post("/api/v1/diaries/{d}/comments/{c}/report", diaryId, commentId), reporter))
                .andExpect(status().isNoContent());
    }

    private String resolve(Session grower, String kind, String payload) throws Exception {
        return body(mvc.perform(authed(post("/api/v1/catalog/{kind}/resolve", kind), grower)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andReturn()).get("item").get("id").asText();
    }

    private String strain(Session grower, String name, String breederId) throws Exception {
        return resolve(grower, "strains", "{\"name\":\"%s\",\"breederId\":\"%s\",\"confirmNew\":true}".formatted(name, breederId));
    }

    private String product(Session grower, String brandId, String name) throws Exception {
        return resolve(grower, "products",
                "{\"brandId\":\"%s\",\"name\":\"%s\",\"category\":\"NUTRIENT\",\"confirmNew\":true}".formatted(brandId, name));
    }

    private String diary(Session grower, String fields) throws Exception {
        String base = """
                {"title":"Catálogo","floweringType":"PHOTOPERIOD","seedSex":"FEMINIZED","medium":"LIVING_SOIL",
                 "visibility":"PUBLIC","startedOn":"%s",""".formatted(LocalDate.now());
        return body(mvc.perform(authed(post("/api/v1/diaries"), grower)
                        .contentType(MediaType.APPLICATION_JSON).content(base + fields.substring(1)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    /** Só letras: a chave do catálogo ignora pontuação, e sufixos numéricos poderiam parecer demais entre si. */
    private static String suffix() {
        StringBuilder suffix = new StringBuilder();
        UUID random = UUID.randomUUID();
        for (char c : random.toString().replace("-", "").substring(0, 8).toCharArray()) {
            suffix.append((char) ('a' + Character.digit(c, 16)));
        }
        return suffix.toString();
    }
}
