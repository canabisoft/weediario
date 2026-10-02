package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TermsAndModerationIntegrationTest extends IntegrationTestSupport {

    // ---------------------------------------------------------------------------------- termos de uso

    @Test
    void registrationRequiresTheCurrentTermsAndStoresOnlyVersionAndTime() throws Exception {
        String username = "t_" + randomLetters(8);
        String base = """
                {"username":"%s","recoveryVerifier":"%s"%s}""";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(base.formatted(username, VERIFIER, ""))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(base.formatted(username, VERIFIER, ",\"acceptedTermsVersion\":\"0.9\"")))
                .andExpect(status().isUnprocessableEntity());
        String token = body(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(base.formatted(username, VERIFIER, ",\"acceptedTermsVersion\":\"1.0\"")))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.termsVersion").value("1.0"))
                .andExpect(jsonPath("$.termsAccepted").value(true))
                .andExpect(jsonPath("$.termsAcceptedAt").isNotEmpty());

        // Só versão e data/hora: nenhuma coluna de IP, user-agent ou aparelho no banco.
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns
                 where table_schema = 'public'
                   and table_name in ('users', 'content_reports', 'moderation_log', 'moderation_holds')
                   and (column_name like '%ip%' or column_name like '%agent%' or column_name like '%device%'
                        or column_name like '%reporter_id%')""", Long.class)).isZero();
    }

    @Test
    void outdatedTermsBlockPublishingUntilAccepted() throws Exception {
        Session grower = newGrower();
        jdbc.update("update users set terms_version = null, terms_accepted_at = null where username = ?", grower.username());
        Session legacy = relogin(grower);

        mvc.perform(authed(post("/api/v1/diaries"), legacy).contentType(MediaType.APPLICATION_JSON).content(diaryJson("Tenda")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TERMS_ACCEPTANCE_REQUIRED"));
        mvc.perform(authed(get("/api/v1/me"), legacy)).andExpect(jsonPath("$.termsAccepted").value(false));
        mvc.perform(get("/api/v1/feed/diaries")).andExpect(status().isOk());

        mvc.perform(authed(put("/api/v1/me/terms"), legacy).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termsAccepted").value(true));
        Session renewed = relogin(grower);
        mvc.perform(authed(post("/api/v1/diaries"), renewed).contentType(MediaType.APPLICATION_JSON).content(diaryJson("Tenda")))
                .andExpect(status().isCreated());
    }

    // --------------------------------------------------------------------------- filtro anti-comércio

    @Test
    void commerceFilterBlocksContactsAndHoldsAdsForModeration() throws Exception {
        Session owner = newGrower();
        Session author = newGrower();
        Session moderator = moderator();
        UUID diaryId = createDiary(owner, "Filtro", "PUBLIC", LocalDate.now().toString());

        mvc.perform(authed(post("/api/v1/diaries/{id}/comments", diaryId), author).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"chama no zap 11 98765-4321\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Termos de Uso")));
        String held = comment(author, diaryId, "vendo 5g R$50, entrego");
        comment(author, diaryId, "pH 6,2 e EC 1.4, gastei R$ 300 no LED");

        mvc.perform(get("/api/v1/diaries/{id}/comments", diaryId))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].body").value("pH 6,2 e EC 1.4, gastei R$ 300 no LED"));
        JsonNode mine = body(mvc.perform(authed(get("/api/v1/diaries/{id}/comments", diaryId), author)).andReturn());
        assertThat(mine.get("totalItems").asInt()).isEqualTo(2);
        assertThat(mine.get("items").findValuesAsText("underReview")).contains("true");

        JsonNode queued = queued(moderator, held);
        assertThat(queued.get("held").asBoolean()).isTrue();
        assertThat(queued.get("cause").asText()).isEqualTo("AUTO_FILTER");
        assertThat(queued.get("detail").asText()).isNotBlank();

        // Diário público com cara de anúncio nasce privado e travado.
        String diary = body(mvc.perform(authed(post("/api/v1/diaries"), owner).contentType(MediaType.APPLICATION_JSON)
                .content(diaryJson("Vendo clones de gelato"))).andExpect(status().isCreated()).andReturn()).get("id").asText();
        mvc.perform(authed(get("/api/v1/diaries/{id}", diary), owner))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.moderationHold").value(true));
        mvc.perform(authed(put("/api/v1/diaries/{id}/visibility", diary), owner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"PUBLIC\"}")).andExpect(status().isUnprocessableEntity());

        // Bio de anúncio: perfil público sem a bio até a moderação decidir.
        mvc.perform(authed(put("/api/v1/me/profile"), author).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bio\":\"aceito pix, faço entrega\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/growers/{u}", author.username())).andExpect(jsonPath("$.bio").doesNotExist());

        // Catálogo não tem fila: nome com contato é recusado.
        mvc.perform(authed(post("/api/v1/catalog/breeders/resolve"), owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Seeds do zap 11987654321\",\"confirmNew\":true}"))
                .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------------- denúncias e decisões auditadas

    @Test
    void threeReportsHideAndTheModeratorDecisionIsAudited() throws Exception {
        Session owner = newGrower();
        Session moderator = moderator();
        UUID diaryId = createDiary(owner, "Denunciado", "PUBLIC", LocalDate.now().toString());

        report(owner, "DIARY", diaryId, "SPAM").andExpect(status().isUnprocessableEntity());
        report(newGrower(), "DIARY", diaryId, "ILLEGAL_COMMERCE").andExpect(jsonPath("$.hidden").value(false));
        Session repeated = newGrower();
        report(repeated, "DIARY", diaryId, "ILLEGAL_COMMERCE").andExpect(status().isOk());
        report(repeated, "DIARY", diaryId, "ILLEGAL_COMMERCE").andExpect(jsonPath("$.hidden").value(false));
        report(newGrower(), "DIARY", diaryId, "TRAFFICKING_APOLOGY").andExpect(jsonPath("$.hidden").value(true));

        mvc.perform(get("/api/v1/diaries/{id}", diaryId)).andExpect(status().isNotFound());
        mvc.perform(authed(get("/api/v1/moderation/queue"), owner)).andExpect(status().isForbidden());
        JsonNode queued = queued(moderator, diaryId.toString());
        assertThat(queued.get("reports").asInt()).isEqualTo(3);
        assertThat(queued.get("reasons").get("ILLEGAL_COMMERCE").asInt()).isEqualTo(2);
        assertThat(queued.get("author").asText()).isEqualTo(owner.username());
        assertThat(queued.toString()).doesNotContain("reporter");

        // Improcedente: volta ao público, sai da fila e fica no log.
        decide(moderator, "DIARY", diaryId, "KEEP", "NONE", null).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/diaries/{id}", diaryId)).andExpect(jsonPath("$.visibility").value("PUBLIC"));
        assertThat(queued(moderator, diaryId.toString())).isNull();
        assertThat(jdbc.queryForObject("select count(*) from content_reports where target_id = ?::uuid", Long.class,
                diaryId.toString())).isZero();
        assertThat(jdbc.queryForObject("select action || ':' || reason from moderation_log where target_id = ?::uuid",
                String.class, diaryId.toString())).isEqualTo("CONTENT_RESTORED:UNFOUNDED");
    }

    @Test
    void removingContentAndBanningTheAuthorLeavesAHashAndBlocksLogin() throws Exception {
        Session owner = newGrower();
        Session seller = newGrower();
        Session moderator = moderator();
        UUID diaryId = createDiary(owner, "Com vendedor", "PUBLIC", LocalDate.now().toString());
        String text = "olha esse material, top demais";
        String commentId = comment(seller, diaryId, text);
        for (int i = 0; i < 3; i++) {
            report(newGrower(), "COMMENT", commentId, "ILLEGAL_COMMERCE").andExpect(status().isOk());
        }

        decide(moderator, "COMMENT", UUID.fromString(commentId), "REMOVE", "BAN", "ILLEGAL_COMMERCE")
                .andExpect(status().isNoContent());

        mvc.perform(authed(get("/api/v1/diaries/{id}/comments", diaryId), owner)).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(seller.username(), seller.accessKey())))
                .andExpect(status().isUnauthorized());
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        assertThat(jdbc.queryForList("""
                select action || ':' || reason || ':' || coalesce(content_sha256, '-') from moderation_log
                 where reported_username = ? order by created_at, action""", String.class, seller.username()))
                .containsExactlyInAnyOrder("CONTENT_REMOVED:ILLEGAL_COMMERCE:" + hash, "USER_BANNED:ILLEGAL_COMMERCE:-");

        // Recurso aceito: só admin desfaz banimento (moderação comprometida não devolve o acesso).
        mvc.perform(authed(post("/api/v1/moderation/users/{u}/reinstate", seller.username()), moderator)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"recurso aceito\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(authed(post("/api/v1/moderation/users/{u}/reinstate", seller.username()), admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"recurso aceito\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(seller.username(), seller.accessKey())))
                .andExpect(status().isOk());

        // Exportação do log: só admin; sem quem denunciou.
        mvc.perform(authed(get("/api/v1/moderation/log.csv").param("username", seller.username()), moderator))
                .andExpect(status().isForbidden());
        MvcResult csv = mvc.perform(authed(get("/api/v1/moderation/log.csv").param("username", seller.username()), admin()))
                .andExpect(status().isOk()).andReturn();
        String content = csv.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(content).contains("USER_BANNED", "USER_REINSTATED", hash, seller.username()).doesNotContain("reporter");
    }

    @Test
    void reportedProfileIsClearedOnRemoval() throws Exception {
        Session grower = newGrower();
        Session moderator = moderator();
        mvc.perform(authed(put("/api/v1/me/profile"), grower).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bio\":\"bio ofensiva qualquer\"}")).andExpect(status().isOk());
        UUID userId = jdbc.queryForObject("select id from users where username = ?", UUID.class, grower.username());
        for (int i = 0; i < 2; i++) {
            report(newGrower(), "PROFILE", userId, "INAPPROPRIATE").andExpect(status().isOk());
        }
        // A tela denuncia perfil pelo apelido (a API pública não expõe o id da conta).
        mvc.perform(authed(post("/api/v1/reports/profiles/{u}", grower.username()), newGrower())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"INAPPROPRIATE\"}"))
                .andExpect(jsonPath("$.hidden").value(true));
        mvc.perform(get("/api/v1/growers/{u}", grower.username())).andExpect(jsonPath("$.bio").doesNotExist());

        decide(moderator, "PROFILE", userId, "REMOVE", "NONE", "INAPPROPRIATE").andExpect(status().isNoContent());
        mvc.perform(authed(get("/api/v1/me"), grower))
                .andExpect(jsonPath("$.bio").doesNotExist())
                .andExpect(jsonPath("$.profileHidden").value(false));
    }

    // ------------------------------------------------------------------------------------- helpers

    private org.springframework.test.web.servlet.ResultActions decide(Session moderator, String type, UUID id, String verdict,
                                                                      String account, String reason) throws Exception {
        return mvc.perform(authed(post("/api/v1/moderation/decisions"), moderator).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"targetType":"%s","targetId":"%s","verdict":"%s","account":"%s"%s}"""
                        .formatted(type, id, verdict, account, reason == null ? "" : ",\"reason\":\"" + reason + "\"")));
    }

    private JsonNode queued(Session moderator, String id) throws Exception {
        JsonNode items = body(mvc.perform(authed(get("/api/v1/moderation/queue?size=50"), moderator))
                .andExpect(status().isOk()).andReturn()).get("items");
        for (JsonNode item : items) {
            if (item.get("id").asText().equals(id)) {
                return item;
            }
        }
        return null;
    }

    private String comment(Session author, UUID diaryId, String text) throws Exception {
        return body(mvc.perform(authed(post("/api/v1/diaries/{id}/comments", diaryId), author)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("body", text))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private Session admin() throws Exception {
        Session session = newGrower();
        jdbc.update("update users set role = 'ADMIN' where username = ?", session.username());
        return relogin(session);
    }

    private static String diaryJson(String title) {
        return """
                {"title":"%s","strainName":"Gelato","floweringType":"PHOTOPERIOD","seedSex":"FEMINIZED",
                 "medium":"LIVING_SOIL","visibility":"PUBLIC","startedOn":"%s"}""".formatted(title, LocalDate.now());
    }
}
