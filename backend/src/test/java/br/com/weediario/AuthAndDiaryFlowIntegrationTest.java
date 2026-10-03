package br.com.weediario;

import br.com.weediario.identity.domain.model.AccessKey;
import br.com.weediario.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthAndDiaryFlowIntegrationTest extends IntegrationTestSupport {

    private static final String COOKIE = "__Secure-wd_rt";

    @Test
    void fullSessionLifecycle() throws Exception {
        register("grower_um");

        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("Grower_Um", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn();

        String setCookie = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth");
        String accessToken = body(login).get("accessToken").asText();
        String refreshToken = login.getResponse().getCookie(COOKIE).getValue();

        // Criação de diário exige o Bearer token e usa o sujeito do JWT como autor.
        mvc.perform(post("/api/v1/diaries").contentType(MediaType.APPLICATION_JSON).content(diaryJson()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/diaries").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON).content(diaryJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.currentWeek").value(1))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));

        // Rotação: o refresh devolve um token novo...
        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, refreshToken)))
                .andExpect(status().isOk())
                .andReturn();
        String rotated = refreshed.getResponse().getCookie(COOKIE).getValue();
        assertThat(rotated).isNotEqualTo(refreshToken);

        // ...e reapresentar o antigo logo em seguida (F5 que perdeu o cookie novo) não derruba a sessão.
        // Fora da janela de tolerância, o reuso revoga a família inteira (ver RefreshSessionUseCaseTest).
        MvcResult retried = mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, refreshToken)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie current = new Cookie(COOKIE, retried.getResponse().getCookie(COOKIE).getValue());
        mvc.perform(post("/api/v1/auth/refresh").cookie(current)).andExpect(status().isOk());

        // Depois do logout, nem o token recém-rotacionado é aceito.
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(COOKIE, rotated)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesSession() throws Exception {
        register("grower_dois");
        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("grower_dois", PASSWORD)))
                .andReturn();
        Cookie refresh = new Cookie(COOKIE, login.getResponse().getCookie(COOKIE).getValue());

        mvc.perform(post("/api/v1/auth/logout").cookie(refresh)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordIsStoredOnlyAsArgon2() throws Exception {
        register("grower_apelido");
        String stored = jdbc.queryForObject("select password_hash from users where username = 'grower_apelido'",
                String.class);
        assertThat(stored).startsWith("$argon2id$").doesNotContain(PASSWORD);

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("Grower_Apelido", PASSWORD)))
                .andExpect(status().isOk());
        // Não existe login por e-mail: o e-mail nunca localiza uma conta.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"apelido@example.org","password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void weakPasswordsAreRefusedAtRegistration() throws Exception {
        for (String weak : new String[]{"curta123", "senha12345", "1234567890", "aaaaaaaaaaaaaa", "grower_cinco!!"}) {
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"grower_cinco","password":"%s","recoveryVerifier":"%s",
                                     "acceptedTermsVersion":"%s"}""".formatted(weak, VERIFIER, TERMS)))
                    .andExpect(status().is4xxClientError());
        }
        assertThat(jdbc.queryForObject("select count(*) from users where username = 'grower_cinco'", Integer.class))
                .isZero();
    }

    @Test
    void loginFailuresAreIndistinguishable() throws Exception {
        register("grower_tres");

        String wrong = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("grower_tres", "outra senha qualquer")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownUser = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("ninguem_aqui", PASSWORD)))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(wrong).get("detail")).isEqualTo(json.readTree(unknownUser).get("detail"));
    }

    @Test
    void accessKeyAccountCreatesPasswordOnItsLastKeyLogin() throws Exception {
        register("grower_chave");
        // Conta como era na época da chave de acesso: sem senha, só o SHA-256 da chave.
        AccessKey key = AccessKey.generate();
        jdbc.update("update users set password_hash = null, access_key_hash = ? where username = 'grower_chave'",
                key.hash().value());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("grower_chave", key.value())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/access-key-login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"grower_chave","accessKey":"%s","newPassword":"curta"}""".formatted(key.value())))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/api/v1/auth/access-key-login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"grower_chave","accessKey":"%s","newPassword":"%s"}"""
                                .formatted(key.value(), PASSWORD)))
                .andExpect(status().isOk());

        // A chave deixou de valer; a senha nova funciona.
        assertThat(jdbc.queryForObject("select access_key_hash from users where username = 'grower_chave'",
                String.class)).isNull();
        mvc.perform(post("/api/v1/auth/access-key-login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"grower_chave","accessKey":"%s","newPassword":"%s"}"""
                                .formatted(key.value(), PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("grower_chave", PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsUnknownFieldsInPayload() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"grower_quatro","password":"%s","recoveryVerifier":"%s",
                                 "acceptedTermsVersion":"1.0","role":"ADMIN"}""".formatted(PASSWORD, VERIFIER)))
                .andExpect(status().isBadRequest());
    }

    private void register(String username) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username)))
                .andExpect(status().isCreated());
    }

    private static String diaryJson() {
        return """
                {"title":"Tenda 60x60","strainName":"Gorilla Glue #4","breeder":"GG Strains",
                 "floweringType":"PHOTOPERIOD","seedSex":"FEMINIZED","medium":"LIVING_SOIL"}""";
    }
}
