package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SocialIntegrationTest extends IntegrationTestSupport {

    @Test
    void reactionsAreAnonymousAndIdempotent() throws Exception {
        Session owner = newGrower();
        Session fan = newGrower();
        UUID diaryId = createDiary(owner, "Reacted", "PUBLIC", LocalDate.now().toString());

        mvc.perform(authed(put("/api/v1/diaries/{id}/reactions/FIRE", diaryId), fan)).andExpect(status().isOk());
        mvc.perform(authed(put("/api/v1/diaries/{id}/reactions/FIRE", diaryId), fan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.FIRE").value(1))
                .andExpect(jsonPath("$.counts.CLAP").value(0))
                .andExpect(jsonPath("$.mine[0]").value("FIRE"));

        // O dono do diário vê a contagem, mas não quem reagiu.
        mvc.perform(authed(get("/api/v1/diaries/{id}/reactions", diaryId), owner))
                .andExpect(jsonPath("$.counts.FIRE").value(1))
                .andExpect(jsonPath("$.mine").isEmpty());

        // No banco não existe o id de quem reagiu.
        String fanId = jdbc.queryForObject("select id::text from users where username = ?", String.class, fan.username());
        List<String> reactors = jdbc.queryForList("select reactor from diary_reactions where diary_id = ?",
                String.class, diaryId);
        assertThat(reactors).hasSize(1).noneMatch(reactor -> reactor.contains(fanId));

        mvc.perform(authed(delete("/api/v1/diaries/{id}/reactions/FIRE", diaryId), fan))
                .andExpect(jsonPath("$.counts.FIRE").value(0));
        mvc.perform(put("/api/v1/diaries/{id}/reactions/FIRE", diaryId)).andExpect(status().isUnauthorized());
    }

    @Test
    void sameGrowerHasUnlinkablePseudonymsAcrossDiaries() throws Exception {
        Session owner = newGrower();
        Session fan = newGrower();
        UUID first = createDiary(owner, "A", "PUBLIC", LocalDate.now().toString());
        UUID second = createDiary(owner, "B", "PUBLIC", LocalDate.now().toString());

        mvc.perform(authed(put("/api/v1/diaries/{id}/reactions/HERB", first), fan)).andExpect(status().isOk());
        mvc.perform(authed(put("/api/v1/diaries/{id}/reactions/HERB", second), fan)).andExpect(status().isOk());

        List<String> pseudonyms = jdbc.queryForList(
                "select reactor from diary_reactions where diary_id in (?, ?)", String.class, first, second);
        assertThat(pseudonyms).hasSize(2).doesNotHaveDuplicates();
    }

    @Test
    void commentsShowNicknameAndCountInFeed() throws Exception {
        Session owner = newGrower();
        Session visitor = newGrower();
        UUID diaryId = createDiary(owner, "Commented", "PUBLIC", LocalDate.now().toString());

        comment(visitor, diaryId, "Qual EC você usa na flora?");
        comment(owner, diaryId, "<script>alert(1)</script> 1.8 na semana 4");

        JsonNode page = body(mvc.perform(get("/api/v1/diaries/{id}/comments", diaryId))
                .andExpect(status().isOk()).andReturn());
        JsonNode first = page.get("items").get(0);
        JsonNode reply = page.get("items").get(1);
        assertThat(first.get("author").asText()).isEqualTo(visitor.username());
        assertThat(first.get("byDiaryAuthor").asBoolean()).isFalse();
        assertThat(first.get("deletable").asBoolean()).isFalse();
        assertThat(reply.get("byDiaryAuthor").asBoolean()).isTrue();
        // Texto é devolvido como veio: o escape é responsabilidade do Angular na renderização.
        assertThat(reply.get("body").asText()).startsWith("<script>");
        assertThat(first.has("authorId")).isFalse();

        mvc.perform(get("/api/v1/diaries/{id}", diaryId)).andExpect(jsonPath("$.commentsCount").value(2));
    }

    @Test
    void diaryOwnerCanDeleteAnyCommentButStrangersCannot() throws Exception {
        Session owner = newGrower();
        Session author = newGrower();
        Session stranger = newGrower();
        UUID diaryId = createDiary(owner, "Moderated", "PUBLIC", LocalDate.now().toString());
        String commentId = comment(author, diaryId, "spam spam spam");

        mvc.perform(authed(delete("/api/v1/diaries/{d}/comments/{c}", diaryId, commentId), stranger))
                .andExpect(status().isNotFound());
        mvc.perform(authed(delete("/api/v1/diaries/{d}/comments/{c}", diaryId, commentId), owner))
                .andExpect(status().isNoContent());
    }

    @Test
    void commentIsHiddenAfterThreeDistinctReports() throws Exception {
        Session owner = newGrower();
        Session author = newGrower();
        UUID diaryId = createDiary(owner, "Reported", "PUBLIC", LocalDate.now().toString());
        String commentId = comment(author, diaryId, "conteúdo abusivo");

        report(author, "COMMENT", commentId, "SPAM").andExpect(status().isUnprocessableEntity()); // o próprio autor não
        Session reporter = trustedGrower();
        report(reporter, diaryId, commentId);
        report(reporter, diaryId, commentId); // repetida: não conta
        report(trustedGrower(), diaryId, commentId);
        assertThat(commentCount(diaryId)).isEqualTo(1);

        report(trustedGrower(), diaryId, commentId);
        assertThat(commentCount(diaryId)).isZero();

    }

    @Test
    void privateDiaryAcceptsNoInteractionFromOthers() throws Exception {
        Session owner = newGrower();
        Session other = newGrower();
        UUID diaryId = createDiary(owner, "Private", "PRIVATE", LocalDate.now().toString());

        mvc.perform(authed(post("/api/v1/diaries/{id}/comments", diaryId), other)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"body":"oi"}"""))
                .andExpect(status().isNotFound());
        mvc.perform(authed(put("/api/v1/diaries/{id}/reactions/CLAP", diaryId), other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/diaries/{id}/comments", diaryId)).andExpect(status().isNotFound());
    }

    @Test
    void commentFloodIsThrottled() throws Exception {
        Session owner = newGrower();
        Session flooder = newGrower();
        UUID diaryId = createDiary(owner, "Flood", "PUBLIC", LocalDate.now().toString());
        for (int i = 0; i < 5; i++) {
            comment(flooder, diaryId, "msg " + i);
        }

        mvc.perform(authed(post("/api/v1/diaries/{id}/comments", diaryId), flooder)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"body":"mais uma"}"""))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void feedCanBeSortedByReactions() throws Exception {
        String strain = "Sort" + UUID.randomUUID().toString().substring(0, 6);
        Session owner = newGrower();
        UUID quiet = createDiary(owner, strain, "PUBLIC", LocalDate.now().toString());
        UUID popular = createDiary(owner, strain, "PUBLIC", LocalDate.now().minusDays(3).toString());
        for (String type : List.of("FIRE", "HERB", "CLAP")) {
            mvc.perform(authed(put("/api/v1/diaries/{id}/reactions/" + type, popular), newGrower()))
                    .andExpect(status().isOk());
        }

        JsonNode feed = body(mvc.perform(get("/api/v1/feed/diaries?sort=MOST_REACTED&strain=" + strain))
                .andExpect(status().isOk()).andReturn());

        assertThat(feed.get("items").get(0).get("id").asText()).isEqualTo(popular.toString());
        assertThat(feed.get("items").get(0).get("reactionsCount").asInt()).isEqualTo(3);
        assertThat(feed.get("items").get(1).get("id").asText()).isEqualTo(quiet.toString());
    }

    private String comment(Session session, UUID diaryId, String text) throws Exception {
        return body(mvc.perform(authed(post("/api/v1/diaries/{id}/comments", diaryId), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("body", text))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private void report(Session session, UUID diaryId, String commentId) throws Exception {
        report(session, "COMMENT", commentId, "INAPPROPRIATE").andExpect(status().isOk());
    }

    private long commentCount(UUID diaryId) throws Exception {
        return body(mvc.perform(get("/api/v1/diaries/{id}/comments", diaryId)).andReturn()).get("totalItems").asLong();
    }
}
