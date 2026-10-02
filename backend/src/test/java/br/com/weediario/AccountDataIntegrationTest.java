package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import br.com.weediario.support.RecordingAccountMailer;
import br.com.weediario.support.TestImages;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Direitos do titular: baixar os dados (zip) e apagar a conta. */
class AccountDataIntegrationTest extends IntegrationTestSupport {

    private static final Path MEDIA = Path.of("target/test-media");

    @Test
    void exportBringsEverythingIncludingPrivateDiariesPhotosAndEmailButNeedsThePassword() throws Exception {
        Session grower = growerWithEmail();
        UUID diaryId = createDiary(grower, "Diário Secreto", "PRIVATE", LocalDate.now().minusDays(10).toString());
        UUID photoId = uploadPhoto(grower, TestImages.phoneJpegWithExif(800, 600, (short) 1));
        publishUpdate(grower, diaryId, photoId);

        mvc.perform(authed(post("/api/v1/me/export"), grower).contentType(MediaType.APPLICATION_JSON)
                        .content(passwordJson("senha errada demais")))
                .andExpect(status().isUnauthorized());

        Map<String, byte[]> zip = unzip(export(grower));
        assertThat(zip).containsKeys("LEIA-ME.txt", "dados.json");
        assertThat(zip.keySet()).anyMatch(name -> name.startsWith("fotos/" + photoId));

        JsonNode data = json.readTree(zip.get("dados.json"));
        assertThat(data.at("/conta/apelido").asText()).isEqualTo(grower.username());
        assertThat(data.at("/conta/email").asText()).isEqualTo(grower.username() + "@exemplo.com");
        assertThat(data.at("/diarios/diarios/0/id").asText()).isEqualTo(diaryId.toString());
        assertThat(data.at("/diarios/diarios/0/visibility").asText()).isEqualTo("PRIVATE");
        assertThat(data.at("/diarios/atualizacoes")).hasSize(1);
        assertThat(data.at("/fotos/0/id").asText()).isEqualTo(photoId.toString());
        // Nada de hash de senha, frase ou e-mail cifrado no pacote.
        assertThat(new String(zip.get("dados.json"))).doesNotContain("$argon2", "password_hash", "recovery_hash");
    }

    @Test
    void deletingTheAccountRemovesItsDataAndFilesButKeepsModerationRecordsAndOpenCatalogItems() throws Exception {
        Session grower = growerWithEmail();
        UUID diaryId = createDiary(grower, "Para Apagar", "PUBLIC", LocalDate.now().minusDays(10).toString());
        UUID photoId = uploadPhoto(grower, TestImages.phoneJpegWithExif(800, 600, (short) 1));
        publishUpdate(grower, diaryId, photoId);
        consentToTreatment(grower);
        mvc.perform(authed(post("/api/v1/me/treatment/patients"), grower).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Eu mesmo\"}")).andExpect(status().isCreated());
        UUID userId = jdbc.queryForObject("select id from users where username = ?", UUID.class, grower.username());
        jdbc.update("""
                insert into moderation_log (id, target_type, target_id, reported_user_id, reported_username, action,
                                            reason, created_at)
                values (?, 'DIARY', ?, ?, ?, 'CONTENT_REMOVED', 'SPAM', now())""",
                UUID.randomUUID(), diaryId, userId, grower.username());
        jdbc.update("update strains set created_by = ? where id = (select id from strains order by name limit 1)", userId);
        assertThat(filesOf(photoId)).isNotEmpty();

        // Confirmação: senha e o apelido digitado.
        mvc.perform(authed(post("/api/v1/me/delete"), grower).contentType(MediaType.APPLICATION_JSON)
                        .content(deleteJson(PASSWORD, "outro_apelido")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(authed(post("/api/v1/me/delete"), grower).contentType(MediaType.APPLICATION_JSON)
                        .content(deleteJson("senha errada demais", grower.username())))
                .andExpect(status().isUnauthorized());
        mvc.perform(authed(post("/api/v1/me/delete"), grower).contentType(MediaType.APPLICATION_JSON)
                        .content(deleteJson(PASSWORD, grower.username().toUpperCase())))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));

        assertThat(count("select count(*) from users where id = ?", userId)).isZero();
        assertThat(count("select count(*) from grow_diaries where owner_id = ?", userId)).isZero();
        assertThat(count("select count(*) from images where owner_id = ?", userId)).isZero();
        assertThat(count("select count(*) from account_emails where user_id = ?", userId)).isZero();
        assertThat(count("select count(*) from treatment_patients where owner_id = ?", userId)).isZero();
        assertThat(filesOf(photoId)).isEmpty();
        // Registro de moderação fica (5 anos), sem vínculo com a conta; o item do catálogo aberto fica, sem autor.
        assertThat(count("select count(*) from moderation_log where target_id = ? and reported_user_id is null",
                diaryId)).isOne();
        assertThat(count("select count(*) from strains where created_by = ?", userId)).isZero();
        assertThat(mail.last(RecordingAccountMailer.Kind.ACCOUNT_DELETED, grower.username())).isPresent();

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(grower.username(), PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminsCannotDeleteThemselvesThroughTheApp() throws Exception {
        Session grower = newGrower();
        jdbc.update("update users set role = 'ADMIN' where username = ?", grower.username());
        Session admin = relogin(grower);

        mvc.perform(authed(post("/api/v1/me/delete"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(deleteJson(PASSWORD, admin.username())))
                .andExpect(status().isForbidden());
    }

    @Test
    void dataRightsWorkEvenBeforeAcceptingNewTerms() throws Exception {
        Session grower = newGrower();
        jdbc.update("update users set terms_version = null, terms_accepted_at = null where username = ?",
                grower.username());
        Session outdated = relogin(grower);

        assertThat(unzip(export(outdated))).containsKey("dados.json");
        mvc.perform(authed(post("/api/v1/me/delete"), outdated).contentType(MediaType.APPLICATION_JSON)
                        .content(deleteJson(PASSWORD, outdated.username())))
                .andExpect(status().isNoContent());
    }

    private Session growerWithEmail() throws Exception {
        String username = "g_" + randomLetters(12);
        MvcResult registered = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, username + "@exemplo.com")))
                .andExpect(status().isCreated())
                .andReturn();
        String token = mail.last(RecordingAccountMailer.Kind.VERIFY_EMAIL, username).orElseThrow().token();
        mvc.perform(post("/api/v1/auth/email/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(token))).andExpect(status().isNoContent());
        return new Session(username, body(registered).get("accessToken").asText(), PASSWORD);
    }

    private void publishUpdate(Session session, UUID diaryId, UUID photoId) throws Exception {
        mvc.perform(authed(post("/api/v1/diaries/{id}/updates", diaryId), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"VEGETATIVE","note":"semana boa","photoIds":["%s"]}""".formatted(photoId)))
                .andExpect(status().isCreated());
    }

    private void consentToTreatment(Session session) throws Exception {
        mvc.perform(authed(put("/api/v1/me/treatment/consent"), session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"termsVersion\":\"2026-09\"}")).andExpect(status().isOk());
    }

    /** O zip sai por streaming (resposta assíncrona no MockMvc). */
    private byte[] export(Session session) throws Exception {
        MvcResult started = mvc.perform(authed(post("/api/v1/me/export"), session)
                        .contentType(MediaType.APPLICATION_JSON).content(passwordJson(PASSWORD)))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString(session.username() + "-dados.zip")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsByteArray();
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.put(entry.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    private static java.util.List<Path> filesOf(UUID imageId) throws IOException {
        try (Stream<Path> files = Files.walk(MEDIA)) {
            return files.filter(path -> path.getFileName().toString().startsWith(imageId.toString())).toList();
        }
    }

    private long count(String sql, Object arg) {
        Long total = jdbc.queryForObject(sql, Long.class, arg);
        return total == null ? 0 : total;
    }

    private static String passwordJson(String password) {
        return """
                {"currentPassword":"%s"}""".formatted(password);
    }

    private static String deleteJson(String password, String username) {
        return """
                {"currentPassword":"%s","username":"%s"}""".formatted(password, username);
    }
}
