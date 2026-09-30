package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationIntegrationTest extends IntegrationTestSupport {

    @Test
    void ownerIsNotifiedOfCommentsReactionsAndFollowersWithoutRevealingAnonymousActors() throws Exception {
        Session owner = newGrower();
        Session fan = newGrower();
        UUID diaryId = createDiary(owner, "Notificada", "PUBLIC", LocalDate.now().toString());

        mvc.perform(authed(put("/api/v1/growers/{u}/follow", owner.username()), fan)).andExpect(status().isNoContent());
        mvc.perform(authed(put("/api/v1/growers/{u}/follow", owner.username()), fan)).andExpect(status().isNoContent());
        comment(fan, diaryId);
        comment(owner, diaryId); // o próprio dono não é notificado
        react(fan, diaryId, "FIRE");
        react(fan, diaryId, "FIRE"); // repetida: não conta
        react(fan, diaryId, "HERB");
        react(owner, diaryId, "CLAP");

        mvc.perform(authed(get("/api/v1/me/notifications/unread-count"), owner)).andExpect(jsonPath("$.unread").value(3));
        JsonNode inbox = inbox(owner);
        assertThat(inbox.findValuesAsText("type")).containsExactly("REACTION", "COMMENT", "NEW_FOLLOWER");

        JsonNode reaction = inbox.get(0);
        assertThat(reaction.get("occurrences").asInt()).isEqualTo(2);
        assertThat(reaction.get("diaryId").asText()).isEqualTo(diaryId.toString());
        assertThat(reaction.has("actor")).isFalse();
        assertThat(inbox.get(1).get("actor").asText()).isEqualTo(fan.username());
        assertThat(inbox.get(2).has("actor")).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from notifications where type in ('REACTION', 'NEW_FOLLOWER') "
                + "and actor_id is not null", Long.class)).isZero();

        mvc.perform(authed(post("/api/v1/me/notifications/read"), owner)).andExpect(status().isNoContent());
        mvc.perform(authed(get("/api/v1/me/notifications/unread-count"), owner)).andExpect(jsonPath("$.unread").value(0));

        // Depois de lida, uma reação nova abre outra notificação.
        react(fan, diaryId, "CLAP");
        JsonNode after = inbox(owner);
        assertThat(after.get(0).get("read").asBoolean()).isFalse();
        assertThat(after.get(0).get("occurrences").asInt()).isEqualTo(1);
        assertThat(after.get(1).get("read").asBoolean()).isTrue();

        String id = after.get(0).get("id").asText();
        mvc.perform(authed(post("/api/v1/me/notifications/{id}/read", id), fan)).andExpect(status().isNoContent());
        mvc.perform(authed(get("/api/v1/me/notifications/unread-count"), owner)).andExpect(jsonPath("$.unread").value(1));
        mvc.perform(authed(post("/api/v1/me/notifications/{id}/read", id), owner)).andExpect(status().isNoContent());
        mvc.perform(authed(get("/api/v1/me/notifications/unread-count"), owner)).andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void followersHearAboutPublicUpdatesOnly() throws Exception {
        Session grower = newGrower();
        Session follower = newGrower();
        mvc.perform(authed(put("/api/v1/growers/{u}/follow", grower.username()), follower)).andExpect(status().isNoContent());
        UUID publicDiary = createDiary(grower, "Pública", "PUBLIC", LocalDate.now().minusDays(3).toString());
        UUID privateDiary = createDiary(grower, "Privada", "PRIVATE", LocalDate.now().minusDays(3).toString());

        publishUpdate(grower, publicDiary);
        publishUpdate(grower, publicDiary);
        publishUpdate(grower, privateDiary);

        JsonNode inbox = inbox(follower);
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).get("type").asText()).isEqualTo("FOLLOWED_UPDATE");
        assertThat(inbox.get(0).get("occurrences").asInt()).isEqualTo(2);
        assertThat(inbox.get(0).get("actor").asText()).isEqualTo(grower.username());
        assertThat(inbox.get(0).get("diaryTitle").asText()).isEqualTo("Tenda de teste");

        // Diário que virou privado some da caixa de quem não é o dono.
        mvc.perform(authed(put("/api/v1/diaries/{id}/visibility", publicDiary), grower)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isOk());
        assertThat(inbox(follower)).isEmpty();
        mvc.perform(authed(get("/api/v1/me/notifications/unread-count"), follower)).andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void streamPushesTheUnreadCounterInRealTime() throws Exception {
        Session owner = newGrower();
        Session fan = newGrower();
        UUID diaryId = createDiary(owner, "Ao vivo", "PUBLIC", LocalDate.now().toString());

        mvc.perform(get("/api/v1/me/notifications/stream")).andExpect(status().isUnauthorized());
        MvcResult stream = mvc.perform(authed(get("/api/v1/me/notifications/stream"), owner)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        assertThat(stream.getResponse().getContentAsString()).contains("event:unread", "data:{\"unread\":0}");

        comment(fan, diaryId);

        // Só o contador trafega no canal; o conteúdo é buscado pela API.
        String events = stream.getResponse().getContentAsString();
        assertThat(events).contains("data:{\"unread\":1}").doesNotContain(fan.username());
        assertThat(stream.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");
    }

    @Test
    void oldestStreamClosesCleanlyWhenTooManyAreOpen() throws Exception {
        Session owner = newGrower();
        MvcResult first = openStream(owner);
        for (int i = 0; i < 5; i++) {
            openStream(owner);
        }

        // O sexto canal fecha o primeiro; o fim do canal passa pela segurança sem virar 401/403.
        mvc.perform(asyncDispatch(first)).andExpect(status().isOk());
    }

    private MvcResult openStream(Session session) throws Exception {
        return mvc.perform(authed(get("/api/v1/me/notifications/stream"), session).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private JsonNode inbox(Session session) throws Exception {
        return body(mvc.perform(authed(get("/api/v1/me/notifications"), session)).andExpect(status().isOk()).andReturn())
                .get("items");
    }

    private void comment(Session author, UUID diaryId) throws Exception {
        mvc.perform(authed(post("/api/v1/diaries/{id}/comments", diaryId), author)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Que planta bonita!\"}"))
                .andExpect(status().isCreated());
    }

    private void react(Session session, UUID diaryId, String type) throws Exception {
        mvc.perform(authed(put("/api/v1/diaries/{id}/reactions/{type}", diaryId, type), session))
                .andExpect(status().isOk());
    }

    private void publishUpdate(Session grower, UUID diaryId) throws Exception {
        mvc.perform(authed(post("/api/v1/diaries/{id}/updates", diaryId), grower)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"VEGETATIVE\",\"note\":\"Tudo verde\"}"))
                .andExpect(status().isCreated());
    }
}
