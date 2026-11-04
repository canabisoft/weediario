package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Moderação com contraditório (Termos de Uso 1.2, cláusula 9): o autor de um conteúdo retido é avisado, manda a sua
 * versão em até 5 dias e a moderação lê junto do conteúdo; a conta banida ainda entra por 30 dias, só para exportar.
 */
class ModerationAppealIntegrationTest extends IntegrationTestSupport {

    @Test
    void authorOfReportedContentIsWarnedAndCanTellTheirSideOnceWithinFiveDays() throws Exception {
        Session owner = newGrower();
        Session moderator = moderator();
        UUID diaryId = createDiary(owner, "Contraditório", "COMMUNITY", LocalDate.now().toString());
        for (int i = 0; i < 3; i++) {
            report(trustedGrower(), "DIARY", diaryId, "ILLEGAL_COMMERCE");
        }

        JsonNode warning = heldNotice(owner, diaryId);
        assertThat(warning).isNotNull();
        assertThat(warning.get("heldType").asText()).isEqualTo("DIARY");
        assertThat(warning.get("diaryId").asText()).isEqualTo(diaryId.toString());
        assertThat(warning.get("appealed").asBoolean()).isFalse();
        assertThat(warning.has("actor")).isFalse();
        Instant until = Instant.parse(warning.get("appealUntil").asText());
        assertThat(until).isBetween(Instant.now().plus(4, ChronoUnit.DAYS), Instant.now().plus(6, ChronoUnit.DAYS));

        // Só o autor, uma vez, com texto de verdade; quem não é autor nem descobre que há retenção.
        appeal(newGrower(), "DIARY", diaryId, "Não é meu, mas quero opinar.").andExpect(status().isNotFound());
        appeal(owner, "DIARY", diaryId, "curto").andExpect(status().isUnprocessableEntity());
        appeal(owner, "DIARY", diaryId, "É um diário de cultivo, não vendo nada.").andExpect(status().isNoContent());
        appeal(owner, "DIARY", diaryId, "Mandando de novo.").andExpect(status().isUnprocessableEntity());
        assertThat(heldNotice(owner, diaryId).get("appealed").asBoolean()).isTrue();

        JsonNode queued = queued(moderator, diaryId);
        assertThat(queued.get("appeal").asText()).isEqualTo("É um diário de cultivo, não vendo nada.");
        assertThat(queued.get("appealedAt").asText()).isNotBlank();

        // Decidido: a retenção some e o aviso deixa de oferecer a manifestação.
        decide(moderator, "DIARY", diaryId, "KEEP", "NONE").andExpect(status().isNoContent());
        assertThat(heldNotice(owner, diaryId).has("appealUntil")).isFalse();
        appeal(owner, "DIARY", diaryId, "Depois da decisão não cabe mais.").andExpect(status().isNotFound());
    }

    @Test
    void appealAfterFiveDaysIsRefused() throws Exception {
        Session owner = newGrower();
        UUID diaryId = createDiary(owner, "Prazo", "COMMUNITY", LocalDate.now().toString());
        for (int i = 0; i < 3; i++) {
            report(trustedGrower(), "DIARY", diaryId, "SPAM");
        }
        jdbc.update("update moderation_holds set held_at = now() - interval '6 days' where target_id = ?::uuid",
                diaryId.toString());

        appeal(owner, "DIARY", diaryId, "Só vi o aviso agora, uma semana depois.")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("O prazo de 5 dias para se manifestar terminou"));
    }

    @Test
    void contentHeldByTheCommerceFilterAlsoWarnsTheAuthor() throws Exception {
        Session owner = newGrower();
        Session author = newGrower();
        UUID diaryId = createDiary(owner, "Filtro", "COMMUNITY", LocalDate.now().toString());
        JsonNode comment = body(mvc.perform(authed(post("/api/v1/diaries/{id}/comments", diaryId), author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("body", "vendo 5g R$50, entrego"))))
                .andExpect(status().isCreated()).andReturn());

        JsonNode warning = heldNotice(author, comment.get("id").asText());
        assertThat(warning.get("heldType").asText()).isEqualTo("COMMENT");
        assertThat(warning.get("diaryId").asText()).isEqualTo(diaryId.toString());
    }

    @Test
    void bannedAccountSignsInForThirtyDaysOnlyToExportItsData() throws Exception {
        Session banned = newGrower();
        Session moderator = moderator();
        UUID diaryId = createDiary(banned, "Banido", "COMMUNITY", LocalDate.now().toString());
        decide(moderator, "DIARY", diaryId, "REMOVE", "BAN").andExpect(status().isNoContent());

        Session back = relogin(banned);
        mvc.perform(authed(get("/api/v1/me"), back))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportOnlyUntil").isNotEmpty());
        mvc.perform(authed(post("/api/v1/me/export"), back).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("currentPassword", banned.password()))))
                .andExpect(status().isOk());
        // Fora da exportação, nada: nem ler o feed, nem publicar.
        mvc.perform(authed(get("/api/v1/feed/diaries"), back)).andExpect(status().isUnauthorized());
        mvc.perform(authed(post("/api/v1/diaries/{id}/comments", diaryId), back).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("body", "oi"))))
                .andExpect(status().isUnauthorized());

        // Passados os 30 dias, a conta não entra mais.
        jdbc.update("update users set status_changed_at = now() - interval '31 days' where username = ?", banned.username());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(banned.username(), banned.password())))
                .andExpect(status().isUnauthorized());
        mvc.perform(authed(get("/api/v1/me"), back)).andExpect(status().isUnauthorized());
    }

    private ResultActions appeal(Session author, String type, UUID id, String text) throws Exception {
        return mvc.perform(authed(post("/api/v1/me/moderation/appeals"), author).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("targetType", type, "targetId", id.toString(), "text", text))));
    }

    private ResultActions decide(Session moderator, String type, UUID id, String verdict, String account) throws Exception {
        return mvc.perform(authed(post("/api/v1/moderation/decisions"), moderator).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"targetType":"%s","targetId":"%s","verdict":"%s","account":"%s","reason":"ILLEGAL_COMMERCE"}"""
                        .formatted(type, id, verdict, account)));
    }

    private JsonNode heldNotice(Session owner, UUID heldId) throws Exception {
        return heldNotice(owner, heldId.toString());
    }

    private JsonNode heldNotice(Session owner, String heldId) throws Exception {
        JsonNode items = body(mvc.perform(authed(get("/api/v1/me/notifications"), owner))
                .andExpect(status().isOk()).andReturn()).get("items");
        for (JsonNode item : items) {
            if ("CONTENT_HELD".equals(item.get("type").asText()) && heldId.equals(item.path("heldId").asText())) {
                return item;
            }
        }
        return null;
    }

    private JsonNode queued(Session moderator, UUID id) throws Exception {
        JsonNode items = body(mvc.perform(authed(get("/api/v1/moderation/queue?size=50"), moderator))
                .andExpect(status().isOk()).andReturn()).get("items");
        for (JsonNode item : items) {
            if (item.get("id").asText().equals(id.toString())) {
                return item;
            }
        }
        return null;
    }
}
