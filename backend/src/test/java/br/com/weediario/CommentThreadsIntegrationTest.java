package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Respostas em um nível e reações anônimas nos comentários de diários e de receitas.
 */
class CommentThreadsIntegrationTest extends IntegrationTestSupport {

    // ------------------------------------------------------------------------------------------------- diários

    @Test
    void repliesAreListedUnderTheirRootCommentAndNeverGoDeeper() throws Exception {
        Session owner = newGrower();
        Session asker = newGrower();
        Session other = newGrower();
        UUID diaryId = createDiary(owner, "Conversa", "PUBLIC", LocalDate.now().toString());

        String root = diaryComment(asker, diaryId, "Qual EC na flora?", null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String rootId = json.readTree(root).get("id").asText();
        String answer = id(diaryComment(owner, diaryId, "1.8 na semana 4", rootId));
        // Responder uma resposta anexa ao mesmo comentário raiz.
        String thanks = id(diaryComment(asker, diaryId, "Valeu!", answer));
        diaryComment(other, diaryId, "Outro assunto", null).andExpect(status().isCreated());

        JsonNode page = body(mvc.perform(get("/api/v1/diaries/{id}/comments", diaryId))
                .andExpect(status().isOk()).andReturn());
        assertThat(page.get("totalItems").asInt()).as("só comentários raiz são paginados").isEqualTo(2);
        JsonNode first = page.get("items").get(0);
        assertThat(first.get("id").asText()).isEqualTo(rootId);
        assertThat(first.has("parentId")).isFalse();
        assertThat(first.get("replyCount").asInt()).isEqualTo(2);
        assertThat(first.get("replies")).hasSize(2);
        assertThat(first.get("replies").get(0).get("id").asText()).isEqualTo(answer);
        assertThat(first.get("replies").get(0).get("byDiaryAuthor").asBoolean()).isTrue();
        assertThat(first.get("replies").get(1).get("id").asText()).isEqualTo(thanks);
        assertThat(first.get("replies").get(1).get("parentId").asText()).isEqualTo(rootId);
        assertThat(first.get("replies").get(0).has("authorId")).isFalse();
        assertThat(jdbc.queryForObject("select parent_id::text from diary_comments where id = ?::uuid", String.class, thanks))
                .isEqualTo(rootId);

        mvc.perform(get("/api/v1/diaries/{id}", diaryId)).andExpect(jsonPath("$.commentsCount").value(4));

        // Responder comentário de outro diário não vale.
        UUID otherDiary = createDiary(owner, "Outro", "PUBLIC", LocalDate.now().toString());
        diaryComment(asker, otherDiary, "oi", rootId).andExpect(status().isNotFound());
    }

    @Test
    void replyPreviewShowsTheFirstRepliesAndTheRestComesFromSeeMore() throws Exception {
        Session owner = newGrower();
        UUID diaryId = createDiary(owner, "Muitas respostas", "PUBLIC", LocalDate.now().toString());
        String rootId = id(diaryComment(owner, diaryId, "Pergunta", null));
        for (int i = 0; i < 7; i++) {
            id(diaryComment(newGrower(), diaryId, "resposta " + i, rootId));
        }

        JsonNode root = body(mvc.perform(get("/api/v1/diaries/{id}/comments", diaryId)).andReturn()).get("items").get(0);
        assertThat(root.get("replyCount").asInt()).isEqualTo(7);
        assertThat(root.get("replies")).hasSize(5);
        assertThat(root.get("replies").get(0).get("body").asText()).isEqualTo("resposta 0");

        JsonNode all = body(mvc.perform(get("/api/v1/diaries/{d}/comments/{c}/replies", diaryId, rootId))
                .andExpect(status().isOk()).andReturn());
        assertThat(all.get("totalItems").asInt()).isEqualTo(7);
        assertThat(all.get("items").get(6).get("body").asText()).isEqualTo("resposta 6");
    }

    @Test
    void heldReplyIsVisibleOnlyToItsAuthorAndGoesToModerationWithoutNotifying() throws Exception {
        Session owner = newGrower();
        Session seller = newGrower();
        UUID diaryId = createDiary(owner, "Filtro nas respostas", "PUBLIC", LocalDate.now().toString());
        String rootId = id(diaryComment(owner, diaryId, "Como ficou a secagem?", null));

        diaryComment(seller, diaryId, "chama no zap 11 98765-4321", rootId).andExpect(status().isUnprocessableEntity());
        String held = id(diaryComment(seller, diaryId, "vendo 5g R$50, entrego", rootId));

        JsonNode publicRoot = body(mvc.perform(get("/api/v1/diaries/{id}/comments", diaryId)).andReturn())
                .get("items").get(0);
        assertThat(publicRoot.get("replyCount").asInt()).isZero();
        assertThat(publicRoot.get("replies")).isEmpty();
        mvc.perform(get("/api/v1/diaries/{id}", diaryId)).andExpect(jsonPath("$.commentsCount").value(1));

        JsonNode sellerRoot = body(mvc.perform(authed(get("/api/v1/diaries/{id}/comments", diaryId), seller)).andReturn())
                .get("items").get(0);
        assertThat(sellerRoot.get("replies").get(0).get("id").asText()).isEqualTo(held);
        assertThat(sellerRoot.get("replies").get(0).get("underReview").asBoolean()).isTrue();

        assertThat(jdbc.queryForObject("select cause from moderation_holds where target_type = 'COMMENT' and target_id = ?::uuid",
                String.class, held)).isEqualTo("AUTO_FILTER");
        assertThat(inboxTypes(owner)).doesNotContain("REPLY");

        // Ninguém responde nem reage ao que não consegue ver.
        diaryComment(newGrower(), diaryId, "oi", held).andExpect(status().isNotFound());
        mvc.perform(authed(put("/api/v1/diaries/{d}/comments/{c}/reactions/FIRE", diaryId, held), owner))
                .andExpect(status().isNotFound());
    }

    @Test
    void replyNotifiesTheAnsweredAuthorButNotYourself() throws Exception {
        Session owner = newGrower();
        Session asker = newGrower();
        UUID diaryId = createDiary(owner, "Avisos de resposta", "PUBLIC", LocalDate.now().toString());
        String rootId = id(diaryComment(asker, diaryId, "Qual substrato?", null));
        id(diaryComment(asker, diaryId, "Esqueci: é auto?", rootId)); // respondeu a si mesmo

        assertThat(inboxTypes(asker)).doesNotContain("REPLY");

        id(diaryComment(owner, diaryId, "Solo vivo, e é auto", rootId));
        JsonNode inbox = inbox(asker);
        assertThat(inbox.get(0).get("type").asText()).isEqualTo("REPLY");
        assertThat(inbox.get(0).get("actor").asText()).isEqualTo(owner.username());
        assertThat(inbox.get(0).get("diaryId").asText()).isEqualTo(diaryId.toString());
        assertThat(inbox.get(0).get("diaryTitle").asText()).isNotBlank();
        // O dono do diário, que respondeu, não recebe aviso de comentário pela própria resposta.
        assertThat(inboxTypes(owner)).containsOnly("COMMENT");
    }

    @Test
    void deletingTheRootDeletesItsRepliesAndRepliesCanBeReportedAndDeleted() throws Exception {
        Session owner = newGrower();
        Session asker = newGrower();
        Session replier = newGrower();
        UUID diaryId = createDiary(owner, "Apagar raiz", "PUBLIC", LocalDate.now().toString());
        String rootId = id(diaryComment(asker, diaryId, "Pergunta", null));
        String reply = id(diaryComment(replier, diaryId, "Resposta", rootId));
        String other = id(diaryComment(replier, diaryId, "Outra resposta", rootId));

        report(asker, "COMMENT", reply, "SPAM").andExpect(status().isOk());
        report(replier, "COMMENT", reply, "SPAM").andExpect(status().isUnprocessableEntity());

        // Estranho não apaga; o autor da resposta e o dono do diário apagam.
        mvc.perform(authed(delete("/api/v1/diaries/{d}/comments/{c}", diaryId, reply), asker))
                .andExpect(status().isNotFound());
        mvc.perform(authed(delete("/api/v1/diaries/{d}/comments/{c}", diaryId, reply), replier))
                .andExpect(status().isNoContent());
        mvc.perform(authed(put("/api/v1/diaries/{d}/comments/{c}/reactions/CLAP", diaryId, other), owner))
                .andExpect(status().isOk());

        mvc.perform(authed(delete("/api/v1/diaries/{d}/comments/{c}", diaryId, rootId), owner))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from diary_comments where diary_id = ?", Long.class, diaryId))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from diary_comment_reactions where comment_id = ?::uuid",
                Long.class, other)).isZero();
    }

    @Test
    void commentReactionsAreAnonymousCountsAndNeverStoreWhoReacted() throws Exception {
        Session owner = newGrower();
        Session fan = newGrower();
        UUID diaryId = createDiary(owner, "Reações em comentários", "PUBLIC", LocalDate.now().toString());
        String rootId = id(diaryComment(owner, diaryId, "Semana 5 de flora", null));
        String reply = id(diaryComment(fan, diaryId, "Que resina!", rootId));

        mvc.perform(authed(put("/api/v1/diaries/{d}/comments/{c}/reactions/FIRE", diaryId, rootId), fan))
                .andExpect(status().isOk());
        mvc.perform(authed(put("/api/v1/diaries/{d}/comments/{c}/reactions/FIRE", diaryId, rootId), fan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.FIRE").value(1))
                .andExpect(jsonPath("$.counts.HERB").value(0))
                .andExpect(jsonPath("$.mine[0]").value("FIRE"));
        mvc.perform(authed(put("/api/v1/diaries/{d}/comments/{c}/reactions/HERB", diaryId, reply), owner))
                .andExpect(status().isOk());

        // A listagem traz contagens para todos e "mine" só para quem reagiu.
        JsonNode anonymous = body(mvc.perform(get("/api/v1/diaries/{id}/comments", diaryId)).andReturn()).get("items").get(0);
        assertThat(anonymous.get("reactions").get("counts").get("FIRE").asInt()).isEqualTo(1);
        assertThat(anonymous.get("reactions").get("mine")).isEmpty();
        assertThat(anonymous.get("replies").get(0).get("reactions").get("counts").get("HERB").asInt()).isEqualTo(1);
        JsonNode asFan = body(mvc.perform(authed(get("/api/v1/diaries/{id}/comments", diaryId), fan)).andReturn())
                .get("items").get(0);
        assertThat(asFan.get("reactions").get("mine").get(0).asText()).isEqualTo("FIRE");
        assertThat(asFan.get("replies").get(0).get("reactions").get("mine")).isEmpty();

        // No banco não existe coluna nem valor com o id de quem reagiu, e o pseudônimo muda de um comentário a outro.
        assertThat(columns("diary_comment_reactions")).containsExactlyInAnyOrder("comment_id", "reactor", "type");
        assertThat(columns("recipe_comment_reactions")).containsExactlyInAnyOrder("comment_id", "reactor", "type");
        String fanId = userId(fan);
        List<String> reactors = jdbc.queryForList("select reactor from diary_comment_reactions where comment_id = ?::uuid",
                String.class, rootId);
        assertThat(reactors).hasSize(1).noneMatch(reactor -> reactor.contains(fanId));
        mvc.perform(authed(put("/api/v1/diaries/{d}/comments/{c}/reactions/FIRE", diaryId, reply), fan))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select reactor from diary_comment_reactions where comment_id = ?::uuid and type = 'FIRE'",
                String.class, reply)).isNotEqualTo(reactors.get(0));
        // Reagir a comentários não notifica ninguém.
        assertThat(inboxTypes(owner)).doesNotContain("REACTION");

        mvc.perform(authed(delete("/api/v1/diaries/{d}/comments/{c}/reactions/FIRE", diaryId, rootId), fan))
                .andExpect(jsonPath("$.counts.FIRE").value(0))
                .andExpect(jsonPath("$.mine").isEmpty());
        mvc.perform(put("/api/v1/diaries/{d}/comments/{c}/reactions/FIRE", diaryId, rootId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void repliesOfSuspendedAccountsDoNotLeak() throws Exception {
        Session owner = newGrower();
        Session suspended = newGrower();
        UUID diaryId = createDiary(owner, "Contas suspensas", "PUBLIC", LocalDate.now().toString());
        String rootId = id(diaryComment(owner, diaryId, "Pergunta", null));
        id(diaryComment(suspended, diaryId, "Resposta", rootId));
        String suspendedRoot = id(diaryComment(suspended, diaryId, "Raiz de suspenso", null));
        id(diaryComment(owner, diaryId, "Resposta para o suspenso", suspendedRoot));

        jdbc.update("update users set status = 'SUSPENDED' where username = ?", suspended.username());

        JsonNode page = body(mvc.perform(get("/api/v1/diaries/{id}/comments", diaryId)).andReturn());
        assertThat(page.get("totalItems").asInt()).isEqualTo(1);
        assertThat(page.get("items").get(0).get("replyCount").asInt()).isZero();
        mvc.perform(get("/api/v1/diaries/{d}/comments/{c}/replies", diaryId, suspendedRoot))
                .andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/diaries/{id}", diaryId)).andExpect(jsonPath("$.commentsCount").value(1));

        // Nem dá para responder ou reagir ao que saiu do ar (mesmo critério da listagem).
        diaryComment(owner, diaryId, "Outra resposta", suspendedRoot).andExpect(status().isNotFound());
        mvc.perform(authed(put("/api/v1/diaries/{d}/comments/{c}/reactions/FIRE", diaryId, suspendedRoot), owner))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------------------------------------ receitas

    @Test
    void recipeCommentsGetRepliesReactionsAndReplyNotifications() throws Exception {
        Session author = newGrower();
        Session asker = newGrower();
        String recipeId = recipe(author);

        String rootId = recipeComment(asker, recipeId, "Funciona em coco?", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.canDelete").value(true))
                .andExpect(jsonPath("$.parentId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        rootId = json.readTree(rootId).get("id").asText();
        String reply = body(recipeComment(author, recipeId, "Funciona, baixe a EC", rootId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(rootId))
                .andExpect(jsonPath("$.author").value(author.username()))
                .andReturn()).get("id").asText();
        String replyToReply = body(recipeComment(asker, recipeId, "Valeu", reply).andReturn()).get("id").asText();
        recipeComment(newGrower(), recipeId, "Outra dúvida", null).andExpect(status().isCreated());

        JsonNode page = body(mvc.perform(authed(get("/api/v1/recipes/{id}/comments", recipeId), asker)).andReturn());
        assertThat(page.get("totalItems").asInt()).isEqualTo(2);
        JsonNode first = page.get("items").get(0);
        assertThat(first.get("body").asText()).isEqualTo("Funciona em coco?");
        assertThat(first.get("canDelete").asBoolean()).isTrue();
        assertThat(first.get("canReport").asBoolean()).isFalse();
        assertThat(first.get("replyCount").asInt()).isEqualTo(2);
        assertThat(first.get("replies").get(0).get("canReport").asBoolean()).isTrue();
        assertThat(first.get("replies").get(1).get("id").asText()).isEqualTo(replyToReply);
        assertThat(first.get("replies").get(1).get("parentId").asText()).isEqualTo(rootId);
        assertThat(first.get("replies").get(0).has("authorId")).isFalse();

        // O autor da pergunta é avisado da resposta (com link para a receita); ninguém é avisado de si mesmo.
        JsonNode inbox = inbox(asker);
        assertThat(inbox.get(0).get("type").asText()).isEqualTo("REPLY");
        assertThat(inbox.get(0).get("recipeId").asText()).isEqualTo(recipeId);
        assertThat(inbox.get(0).get("recipeName").asText()).startsWith("Receita conversada");
        assertThat(inbox.get(0).get("actor").asText()).isEqualTo(author.username());
        assertThat(inboxTypes(author)).containsExactly("REPLY");

        // Reações anônimas.
        mvc.perform(authed(put("/api/v1/recipes/{r}/comments/{c}/reactions/CLAP", recipeId, reply), asker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.CLAP").value(1))
                .andExpect(jsonPath("$.mine[0]").value("CLAP"));
        JsonNode publicView = body(mvc.perform(signedIn(get("/api/v1/recipes/{id}/comments", recipeId))).andReturn());
        assertThat(publicView.get("items").get(0).get("replies").get(0).get("reactions").get("counts").get("CLAP").asInt())
                .isEqualTo(1);
        assertThat(publicView.get("items").get(0).get("replies").get(0).get("reactions").get("mine")).isEmpty();
        String askerId = userId(asker);
        assertThat(jdbc.queryForList("select reactor from recipe_comment_reactions where comment_id = ?::uuid",
                String.class, reply)).hasSize(1).noneMatch(reactor -> reactor.contains(askerId));
        mvc.perform(authed(delete("/api/v1/recipes/{r}/comments/{c}/reactions/CLAP", recipeId, reply), asker))
                .andExpect(jsonPath("$.counts.CLAP").value(0));

        // "Ver mais" e apagar a raiz leva as respostas junto.
        mvc.perform(signedIn(get("/api/v1/recipes/{r}/comments/{c}/replies", recipeId, rootId)))
                .andExpect(jsonPath("$.totalItems").value(2));
        report(author, "RECIPE_COMMENT", replyToReply, "SPAM").andExpect(status().isOk());
        mvc.perform(authed(delete("/api/v1/recipes/{r}/comments/{c}", recipeId, rootId), author))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from recipe_comments where id in (?::uuid, ?::uuid)",
                Long.class, reply, replyToReply)).isZero();
    }

    @Test
    void heldRecipeReplyIsVisibleOnlyToItsAuthor() throws Exception {
        Session author = newGrower();
        Session seller = newGrower();
        String recipeId = recipe(author);
        String rootId = body(recipeComment(author, recipeId, "Dúvidas aqui", null).andReturn()).get("id").asText();

        String held = body(recipeComment(seller, recipeId, "vendo 5g R$50, entrego", rootId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hidden").value(true))
                .andReturn()).get("id").asText();

        JsonNode publicRoot = body(mvc.perform(signedIn(get("/api/v1/recipes/{id}/comments", recipeId))).andReturn())
                .get("items").get(0);
        assertThat(publicRoot.get("replyCount").asInt()).isZero();
        JsonNode sellerRoot = body(mvc.perform(authed(get("/api/v1/recipes/{id}/comments", recipeId), seller)).andReturn())
                .get("items").get(0);
        assertThat(sellerRoot.get("replies").get(0).get("id").asText()).isEqualTo(held);
        assertThat(sellerRoot.get("replies").get(0).get("hidden").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject(
                "select cause from moderation_holds where target_type = 'RECIPE_COMMENT' and target_id = ?::uuid",
                String.class, held)).isEqualTo("AUTO_FILTER");
        assertThat(inboxTypes(author)).doesNotContain("REPLY");
        recipeComment(newGrower(), recipeId, "oi", held).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------------------------------------ apoio

    private ResultActions diaryComment(Session session, UUID diaryId, String text, String parentId) throws Exception {
        return mvc.perform(authed(post("/api/v1/diaries/{id}/comments", diaryId), session)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request(text, parentId))));
    }

    private ResultActions recipeComment(Session session, String recipeId, String text, String parentId) throws Exception {
        return mvc.perform(authed(post("/api/v1/recipes/{id}/comments", recipeId), session)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request(text, parentId))));
    }

    private static Map<String, String> request(String text, String parentId) {
        Map<String, String> request = new HashMap<>();
        request.put("body", text);
        if (parentId != null) {
            request.put("parentId", parentId);
        }
        return request;
    }

    private String id(ResultActions created) throws Exception {
        return body(created.andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private String recipe(Session session) throws Exception {
        return body(mvc.perform(authed(post("/api/v1/recipes"), session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Receita conversada %s","kind":"MINERAL","application":"FERTIGATION",
                                 "batchLiters":10,"visibility":"PUBLIC",
                                 "ingredients":[{"name":"MKP","quantity":1,"unit":"G"}]}"""
                                .formatted(UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private JsonNode inbox(Session session) throws Exception {
        return body(mvc.perform(authed(get("/api/v1/me/notifications"), session)).andExpect(status().isOk()).andReturn())
                .get("items");
    }

    private List<String> inboxTypes(Session session) throws Exception {
        return inbox(session).findValuesAsText("type");
    }

    private List<String> columns(String table) {
        return jdbc.queryForList("select column_name from information_schema.columns where table_name = ?",
                String.class, table);
    }

    private String userId(Session session) {
        return jdbc.queryForObject("select id::text from users where username = ?", String.class, session.username());
    }
}
