package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Conta sem e-mail: cadastro exige a frase de recuperação (só o derivado chega ao servidor) e a recuperação troca a
 * senha, encerra as sessões antigas e entra na conta.
 */
class AccountRecoveryIntegrationTest extends IntegrationTestSupport {

    private static final String COOKIE = "__Secure-wd_rt";
    private static final String NEW_PASSWORD = "outra-senha-bem-longa-456";
    private static final String OTHER_VERIFIER = "b2".repeat(64);

    @Test
    void usersTableHasNoEmailAndStoresOnlyTheHashOfTheVerifier() throws Exception {
        Session grower = newGrower();

        List<String> columns = jdbc.queryForList("""
                select column_name from information_schema.columns
                 where table_schema = 'public' and table_name = 'users'""", String.class);
        assertThat(columns).doesNotContain("email").contains("recovery_hash");
        String stored = jdbc.queryForObject("select recovery_hash from users where username = ?", String.class,
                grower.username());
        assertThat(stored).startsWith("$argon2").doesNotContain(VERIFIER);

        mvc.perform(authed(get("/api/v1/me"), grower))
                .andExpect(jsonPath("$.hasRecoveryPhrase").value(true))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void registrationRequiresAWellFormedVerifier() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"sem_frase","password":"%s","acceptedTermsVersion":"1.0"}""".formatted(PASSWORD)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"frase_ruim","password":"%s","recoveryVerifier":"abandon ability able",
                         "acceptedTermsVersion":"1.0"}""".formatted(PASSWORD)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void recoveryChangesThePasswordRevokesOldSessionsAndSignsIn() throws Exception {
        Session grower = newGrower();
        MvcResult oldLogin = login(grower.username(), PASSWORD);
        Cookie oldRefresh = new Cookie(COOKIE, oldLogin.getResponse().getCookie(COOKIE).getValue());

        MvcResult recovered = mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content(recoverJson(grower.username().toUpperCase(), VERIFIER, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        assertThat(recovered.getResponse().getCookie(COOKIE)).isNotNull();

        // A sessão antiga (talvez num aparelho perdido) cai; a senha antiga deixa de valer; a nova funciona.
        mvc.perform(post("/api/v1/auth/refresh").cookie(oldRefresh)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(grower.username(), PASSWORD)))
                .andExpect(status().isUnauthorized());
        login(grower.username(), NEW_PASSWORD);
    }

    @Test
    void wrongPhraseAndUnknownUserGetTheSameAnswerAndAreThrottled() throws Exception {
        Session grower = newGrower();

        String wrongPhrase = mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content(recoverJson(grower.username(), OTHER_VERIFIER, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownUser = mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content(recoverJson("ninguem_aqui", VERIFIER, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(wrongPhrase).get("detail")).isEqualTo(json.readTree(unknownUser).get("detail"));

        for (int i = 1; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                            .content(recoverJson(grower.username(), OTHER_VERIFIER, NEW_PASSWORD)))
                    .andExpect(status().isUnauthorized());
        }
        // Bloqueado: nem a frase certa passa até o bloqueio acabar.
        mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content(recoverJson(grower.username(), VERIFIER, NEW_PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        // O limite da recuperação é separado do login.
        login(grower.username(), PASSWORD);
    }

    @Test
    void legacyAccountGeneratesThePhraseWithItsPasswordAndCanThenRecover() throws Exception {
        Session grower = newGrower();
        jdbc.update("update users set recovery_hash = null where username = ?", grower.username());

        mvc.perform(authed(get("/api/v1/me"), grower)).andExpect(jsonPath("$.hasRecoveryPhrase").value(false));
        // Conta sem frase não tem como ser recuperada.
        mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content(recoverJson(grower.username(), VERIFIER, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());

        // Só com a senha atual (um token roubado não basta para trocar a frase).
        mvc.perform(authed(put("/api/v1/me/recovery"), grower).contentType(MediaType.APPLICATION_JSON).content("""
                        {"recoveryVerifier":"%s","currentPassword":"senha-errada-errada"}""".formatted(OTHER_VERIFIER)))
                .andExpect(status().isUnauthorized());
        mvc.perform(authed(put("/api/v1/me/recovery"), grower).contentType(MediaType.APPLICATION_JSON).content("""
                        {"recoveryVerifier":"%s","currentPassword":"%s"}""".formatted(OTHER_VERIFIER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasRecoveryPhrase").value(true));

        mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content(recoverJson(grower.username(), OTHER_VERIFIER, NEW_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void settingThePhraseIsAllowedEvenBeforeAcceptingNewTerms() throws Exception {
        Session grower = newGrower();
        jdbc.update("update users set terms_version = null, terms_accepted_at = null where username = ?",
                grower.username());
        String token = body(login(grower.username(), PASSWORD)).get("accessToken").asText();

        mvc.perform(put("/api/v1/me/recovery").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"recoveryVerifier":"%s","currentPassword":"%s"}""".formatted(OTHER_VERIFIER, PASSWORD)))
                .andExpect(status().isOk());
    }

    private MvcResult login(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static String loginJson(String username, String password) {
        return """
                {"login":"%s","password":"%s"}""".formatted(username, password);
    }

    private static String recoverJson(String username, String verifier, String newPassword) {
        return """
                {"username":"%s","recoveryVerifier":"%s","newPassword":"%s"}""".formatted(username, verifier, newPassword);
    }
}
