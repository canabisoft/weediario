package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Painel do admin (métricas e equipe), hierarquia da equipe (403) e corte imediato de contas suspensas.
 */
class AdminPanelIntegrationTest extends IntegrationTestSupport {

    // -------------------------------------------------------------------------------------------- métricas

    @Test
    void metricsAreAggregatedAndAdminOnly() throws Exception {
        Session grower = newGrower();
        createDiary(grower, "Metric Kush", "PUBLIC", LocalDate.now().minusDays(3).toString());

        String body = mvc.perform(authed(get("/api/v1/admin/metrics"), admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts.total").isNumber())
                .andExpect(jsonPath("$.accounts.active").isNumber())
                .andExpect(jsonPath("$.content.publicDiaries").isNumber())
                .andExpect(jsonPath("$.published").isNumber())
                .andExpect(jsonPath("$.last7Days.activeGrowers").isNumber())
                .andExpect(jsonPath("$.moderation.pending").isNumber())
                .andReturn().getResponse().getContentAsString();
        JsonNode metrics = json.readTree(body);
        assertThat(metrics.get("accounts").get("total").asLong()).isPositive();
        assertThat(metrics.get("last7Days").get("newDiaries").asLong()).isPositive();
        // Só números: nenhum apelido, id ou dado de tratamento.
        assertThat(body).doesNotContain(grower.username()).doesNotContainIgnoringCase("treatment");

        mvc.perform(authed(get("/api/v1/admin/metrics"), moderator())).andExpect(status().isForbidden());
        mvc.perform(authed(get("/api/v1/admin/metrics"), grower)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/metrics")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------------------- equipe

    @Test
    void adminPromotesAndRemovesModeratorsAndEveryChangeIsLogged() throws Exception {
        Session admin = admin();
        Session grower = newGrower();

        mvc.perform(authed(post("/api/v1/admin/team/{u}/promote", grower.username()), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"ajuda na fila\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MODERATOR"));

        // O token antigo (papel GROWER) é recusado na hora; renovando, vem o papel novo.
        mvc.perform(authed(get("/api/v1/me"), grower))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_OUTDATED"));
        Session promoted = relogin(grower);
        mvc.perform(authed(get("/api/v1/moderation/queue"), promoted)).andExpect(status().isOk());

        JsonNode team = body(mvc.perform(authed(get("/api/v1/admin/team"), admin)).andExpect(status().isOk()).andReturn());
        assertThat(team.findValuesAsText("username")).contains(promoted.username(), admin.username());

        mvc.perform(authed(post("/api/v1/admin/team/{u}/remove", promoted.username()), admin))
                .andExpect(status().isNoContent());
        mvc.perform(authed(get("/api/v1/moderation/queue"), relogin(promoted))).andExpect(status().isForbidden());

        assertThat(jdbc.queryForList("""
                select action || ':' || reason from moderation_log where reported_username = ? order by created_at""",
                String.class, promoted.username()))
                .containsExactly("ROLE_GRANTED:STAFF_CHANGE", "ROLE_REVOKED:STAFF_CHANGE");
    }

    @Test
    void onlyAdminsManageTheTeamAndAdminsAreNeverChangedThroughTheApi() throws Exception {
        Session admin = admin();
        Session otherAdmin = admin();
        Session moderator = moderator();
        Session grower = newGrower();

        // Moderador não promove nem tira ninguém (nem pela rota, nem chamando o caso de uso).
        mvc.perform(authed(post("/api/v1/admin/team/{u}/promote", grower.username()), moderator))
                .andExpect(status().isForbidden());
        mvc.perform(authed(post("/api/v1/admin/team/{u}/remove", moderator.username()), moderator))
                .andExpect(status().isForbidden());
        mvc.perform(authed(get("/api/v1/admin/team"), moderator)).andExpect(status().isForbidden());

        // Admin não muda o próprio papel nem tira outro admin; ninguém vira admin pela API.
        mvc.perform(authed(post("/api/v1/admin/team/{u}/remove", admin.username()), admin))
                .andExpect(status().isForbidden());
        mvc.perform(authed(post("/api/v1/admin/team/{u}/remove", otherAdmin.username()), admin))
                .andExpect(status().isForbidden());
        mvc.perform(authed(post("/api/v1/admin/team/{u}/promote", moderator.username()), admin))
                .andExpect(status().isUnprocessableEntity());

        // Conta suspensa não entra na equipe.
        jdbc.update("update users set status = 'SUSPENDED' where username = ?", grower.username());
        mvc.perform(authed(post("/api/v1/admin/team/{u}/promote", grower.username()), admin))
                .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------------------------------------- hierarquia

    @Test
    void moderatorsNeverActOnStaffAccountsOrContent() throws Exception {
        Session moderator = moderator();
        Session otherModerator = moderator();
        Session admin = admin();
        Session owner = newGrower();
        UUID diaryId = createDiary(owner, "Staff Haze", "PUBLIC", LocalDate.now().minusDays(10).toString());

        String byModerator = comment(otherModerator, diaryId, "Comentário de alguém da equipe");
        decide(moderator, byModerator, "REMOVE", "BAN")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("equipe")));
        decide(moderator, byModerator, "REMOVE", "NONE").andExpect(status().isForbidden());

        String byAdmin = comment(admin, diaryId, "Comentário do admin");
        decide(moderator, byAdmin, "REMOVE", "SUSPEND").andExpect(status().isForbidden());

        // Admin remove conteúdo de moderador, mas nem admin suspende alguém da equipe por aqui.
        decide(admin, byModerator, "REMOVE", "SUSPEND").andExpect(status().isForbidden());
        decide(admin, byModerator, "REMOVE", "NONE").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select status from users where username = ?", String.class,
                otherModerator.username())).isEqualTo("ACTIVE");
    }

    @Test
    void nobodyModeratesTheirOwnContentOrApprovesTheirOwnRequest() throws Exception {
        Session moderator = moderator();
        Session owner = newGrower();
        UUID diaryId = createDiary(owner, "Self Haze", "PUBLIC", LocalDate.now().minusDays(10).toString());

        // Comentário do próprio moderador retido pelo filtro: ele não consegue liberar.
        String held = comment(moderator, diaryId, "vendo 5g por R$ 50, entrego");
        mvc.perform(authed(post("/api/v1/moderation/decisions"), moderator).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("targetType", "COMMENT", "targetId", held,
                                "verdict", "KEEP"))))
                .andExpect(status().isForbidden());
        mvc.perform(authed(post("/api/v1/moderation/decisions"), moderator()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("targetType", "COMMENT", "targetId", held,
                                "verdict", "KEEP"))))
                .andExpect(status().isNoContent());

        // Pedido de representação do próprio moderador: outra pessoa da equipe aprova.
        String breederId = body(mvc.perform(authed(post("/api/v1/catalog/breeders/resolve"), moderator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Banco Proprio %s\",\"confirmNew\":true}".formatted(UUID.randomUUID())))
                .andReturn()).get("item").get("id").asText();
        mvc.perform(authed(post("/api/v1/breeder-program/representations"), moderator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"breederId\":\"%s\",\"note\":\"Sou o dono deste banco, veja o site.\"}".formatted(breederId)))
                .andExpect(status().isCreated());
        String moderatorId = jdbc.queryForObject("select id::text from users where username = ?", String.class,
                moderator.username());
        mvc.perform(authed(post("/api/v1/moderation/breeder-representatives/{b}/{u}/approve", breederId, moderatorId),
                        moderator))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyAdminsLiftABanButModeratorsCanLiftASuspension() throws Exception {
        Session moderator = moderator();
        Session owner = newGrower();
        Session banned = newGrower();
        Session suspended = newGrower();
        UUID diaryId = createDiary(owner, "Ban Kush", "PUBLIC", LocalDate.now().minusDays(10).toString());
        decide(moderator, comment(banned, diaryId, "comentário a banir"), "REMOVE", "BAN").andExpect(status().isNoContent());
        decide(moderator, comment(suspended, diaryId, "comentário a suspender"), "REMOVE", "SUSPEND")
                .andExpect(status().isNoContent());

        mvc.perform(authed(post("/api/v1/moderation/users/{u}/reinstate", banned.username()), moderator))
                .andExpect(status().isForbidden());
        mvc.perform(authed(post("/api/v1/moderation/users/{u}/reinstate", suspended.username()), moderator))
                .andExpect(status().isNoContent());
        mvc.perform(authed(post("/api/v1/moderation/users/{u}/reinstate", banned.username()), admin()))
                .andExpect(status().isNoContent());
    }

    @Test
    void moderatorsCannotDeleteCommentsDirectlyOnlyThroughTheAuditedDecision() throws Exception {
        Session moderator = moderator();
        Session admin = admin();
        Session owner = newGrower();
        UUID diaryId = createDiary(owner, "Direct Haze", "PUBLIC", LocalDate.now().minusDays(10).toString());
        String byAdmin = comment(admin, diaryId, "Comentário do admin");

        // O caminho direto não aceita moderador (nem deixa rastro); só autor e dono do diário.
        mvc.perform(authed(delete("/api/v1/diaries/{d}/comments/{c}", diaryId, byAdmin), moderator))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from diary_comments where id = ?::uuid", Long.class, byAdmin))
                .isEqualTo(1);
        mvc.perform(authed(delete("/api/v1/diaries/{d}/comments/{c}", diaryId, byAdmin), owner))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------------------------- corte imediato

    @Test
    void suspendedOrBannedAccountsLoseAccessImmediately() throws Exception {
        Session moderator = moderator();
        Session owner = newGrower();
        Session offender = newGrower();
        UUID diaryId = createDiary(owner, "Cut Kush", "PUBLIC", LocalDate.now().minusDays(10).toString());
        String commentId = comment(offender, diaryId, "Comentário que vai sair do ar");

        mvc.perform(authed(get("/api/v1/me"), offender)).andExpect(status().isOk());
        decide(moderator, commentId, "REMOVE", "BAN").andExpect(status().isNoContent());

        // O access token ainda não expirou, mas já não serve.
        mvc.perform(authed(get("/api/v1/me"), offender))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        mvc.perform(authed(post("/api/v1/diaries"), offender).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(offender.username(), offender.password())))
                .andExpect(status().isUnauthorized());
    }

    // --------------------------------------------------------------------------------------------- helpers

    private Session admin() throws Exception {
        Session session = newGrower();
        jdbc.update("update users set role = 'ADMIN' where username = ?", session.username());
        return relogin(session);
    }

    private String comment(Session author, UUID diaryId, String text) throws Exception {
        return body(mvc.perform(authed(post("/api/v1/diaries/{id}/comments", diaryId), author)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("body", text))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private ResultActions decide(Session moderator, String commentId, String verdict, String account) throws Exception {
        return mvc.perform(authed(post("/api/v1/moderation/decisions"), moderator)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("targetType", "COMMENT", "targetId", commentId,
                        "verdict", verdict, "account", account, "reason", "INAPPROPRIATE"))));
    }
}
