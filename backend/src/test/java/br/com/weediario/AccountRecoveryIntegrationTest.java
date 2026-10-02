package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Conta sem e-mail e sem senha: o cadastro devolve a chave de acesso uma vez e exige a frase de recuperação (só o
 * derivado chega ao servidor). A frase gera uma chave nova, encerra as sessões antigas e entra na conta.
 */
class AccountRecoveryIntegrationTest extends IntegrationTestSupport {

    private static final String COOKIE = "__Secure-wd_rt";
    private static final String OTHER_VERIFIER = "b2".repeat(64);

    @Test
    void storesOnlyHashesAndNoEmailOrPassword() throws Exception {
        Session grower = newGrower();

        List<String> columns = jdbc.queryForList("""
                select column_name from information_schema.columns
                 where table_schema = 'public' and table_name = 'users'""", String.class);
        assertThat(columns).doesNotContain("email", "password_hash").contains("access_key_hash", "recovery_hash");
        String recovery = jdbc.queryForObject("select recovery_hash from users where username = ?", String.class,
                grower.username());
        assertThat(recovery).startsWith("$argon2").doesNotContain(VERIFIER);
        assertThat(jdbc.queryForObject("select count(*) from users where access_key_hash = ?", Long.class,
                grower.accessKey())).isZero();

        mvc.perform(authed(get("/api/v1/me"), grower))
                .andExpect(jsonPath("$.hasRecoveryPhrase").value(true))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void registrationReturnsTheKeyOnceWithoutCachingAndRequiresAWellFormedVerifier() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("chave_unica")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.accessKey").value(org.hamcrest.Matchers.startsWith("wd_sec_")))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"sem_frase","acceptedTermsVersion":"1.0"}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"frase_ruim","recoveryVerifier":"abandon ability able","acceptedTermsVersion":"1.0"}"""))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void recoveryIssuesANewKeyRevokesOldSessionsAndSignsIn() throws Exception {
        Session grower = newGrower();
        MvcResult oldLogin = login(grower.username(), grower.accessKey());
        Cookie oldRefresh = new Cookie(COOKIE, oldLogin.getResponse().getCookie(COOKIE).getValue());

        MvcResult recovered = mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content(recoverJson(grower.username().toUpperCase(), VERIFIER)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        String newKey = body(recovered).get("accessKey").asText();
        assertThat(newKey).startsWith("wd_sec_").isNotEqualTo(grower.accessKey());

        // A sessão antiga (talvez num aparelho perdido) cai; a chave antiga deixa de valer; a nova funciona.
        mvc.perform(post("/api/v1/auth/refresh").cookie(oldRefresh)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(grower.username(), grower.accessKey())))
                .andExpect(status().isUnauthorized());
        login(grower.username(), newKey);
    }

    @Test
    void wrongPhraseAndUnknownUserGetTheSameAnswerAndNoOneCanLockTheOwnerOut() throws Exception {
        Session grower = newGrower();

        String wrongPhrase = mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content(recoverJson(grower.username(), OTHER_VERIFIER)))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownUser = mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content(recoverJson("ninguem_aqui", VERIFIER)))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(wrongPhrase).get("detail")).isEqualTo(json.readTree(unknownUser).get("detail"));

        // Um atacante errando de propósito não tranca a conta: a frase tem 128 bits e o dono continua entrando.
        for (int i = 0; i < 8; i++) {
            mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                            .content(recoverJson(grower.username(), OTHER_VERIFIER)))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson(grower.username(), "wd_sec_" + "A".repeat(43))))
                    .andExpect(status().isUnauthorized());
        }
        login(grower.username(), grower.accessKey());
        mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content(recoverJson(grower.username(), VERIFIER)))
                .andExpect(status().isOk());
    }

    @Test
    void legacyAccountGeneratesThePhraseWithItsKeyAndCanThenRecover() throws Exception {
        Session grower = newGrower();
        jdbc.update("update users set recovery_hash = null where username = ?", grower.username());

        mvc.perform(authed(get("/api/v1/me"), grower)).andExpect(jsonPath("$.hasRecoveryPhrase").value(false));
        // Conta sem frase não tem como ser recuperada.
        mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content(recoverJson(grower.username(), VERIFIER)))
                .andExpect(status().isUnauthorized());

        // Só com a chave atual (um token roubado não basta para trocar a frase).
        mvc.perform(authed(put("/api/v1/me/recovery"), grower).contentType(MediaType.APPLICATION_JSON).content("""
                        {"recoveryVerifier":"%s","currentAccessKey":"wd_sec_errada"}""".formatted(OTHER_VERIFIER)))
                .andExpect(status().isUnauthorized());
        mvc.perform(authed(put("/api/v1/me/recovery"), grower).contentType(MediaType.APPLICATION_JSON).content("""
                        {"recoveryVerifier":"%s","currentAccessKey":"%s"}""".formatted(OTHER_VERIFIER, grower.accessKey())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasRecoveryPhrase").value(true));

        mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content(recoverJson(grower.username(), OTHER_VERIFIER)))
                .andExpect(status().isOk());
    }

    @Test
    void settingThePhraseIsAllowedEvenBeforeAcceptingNewTerms() throws Exception {
        Session grower = newGrower();
        jdbc.update("update users set terms_version = null, terms_accepted_at = null where username = ?",
                grower.username());
        Session legacy = relogin(grower);

        mvc.perform(authed(put("/api/v1/me/recovery"), legacy).contentType(MediaType.APPLICATION_JSON).content("""
                        {"recoveryVerifier":"%s","currentAccessKey":"%s"}""".formatted(OTHER_VERIFIER, grower.accessKey())))
                .andExpect(status().isOk());
    }

    @org.springframework.beans.factory.annotation.Autowired
    private br.com.weediario.identity.infrastructure.config.IdentityUseCaseConfig.LegacyPasswordJanitor legacyPasswords;

    @Test
    void oldUnusedPasswordsAreErasedOnlyWhenThePhraseCanStillRecoverTheAccount() throws Exception {
        Session withPhrase = newGrower();
        Session withoutPhrase = newGrower();
        jdbc.update("""
                update users set legacy_password_hash = '$argon2id$antiga', access_key_hash = null,
                                 updated_at = now() - interval '200 days'
                 where username in (?, ?)""", withPhrase.username(), withoutPhrase.username());
        jdbc.update("update users set recovery_hash = null where username = ?", withoutPhrase.username());

        legacyPasswords.purge();

        assertThat(jdbc.queryForObject("select legacy_password_hash from users where username = ?", String.class,
                withPhrase.username())).isNull();
        // Sem frase, a senha é o único jeito de voltar: fica.
        assertThat(jdbc.queryForObject("select legacy_password_hash from users where username = ?", String.class,
                withoutPhrase.username())).isNotNull();
        // Quem ficou só com a frase recupera a conta e ganha uma chave.
        mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content(recoverJson(withPhrase.username(), VERIFIER)))
                .andExpect(status().isOk());
    }

    private MvcResult login(String username, String accessKey) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, accessKey)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static String recoverJson(String username, String verifier) {
        return """
                {"username":"%s","recoveryVerifier":"%s"}""".formatted(username, verifier);
    }
}
