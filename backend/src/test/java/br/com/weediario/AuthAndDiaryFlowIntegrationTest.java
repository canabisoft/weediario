package br.com.weediario;

import br.com.weediario.identity.domain.service.PasswordHasher;
import br.com.weediario.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private PasswordHasher passwordHasher;

    @Test
    void fullSessionLifecycle() throws Exception {
        String key = register("grower_um");

        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("Grower_Um", key)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
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
        String key = register("grower_dois");
        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("grower_dois", key)))
                .andReturn();
        Cookie refresh = new Cookie(COOKIE, login.getResponse().getCookie(COOKIE).getValue());

        mvc.perform(post("/api/v1/auth/logout").cookie(refresh)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void logsInWithUsernameAndAccessKeyOnly() throws Exception {
        String key = register("grower_apelido");
        assertThat(key).startsWith("wd_sec_").hasSize(50);
        // O banco guarda só o SHA-256 da chave; nem senha nem e-mail existem mais.
        assertThat(jdbc.queryForObject("select access_key_hash from users where username = 'grower_apelido'", String.class))
                .hasSize(64).doesNotContain(key.substring(7));
        assertThat(jdbc.queryForObject("select legacy_password_hash from users where username = 'grower_apelido'",
                String.class)).isNull();

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("Grower_Apelido", key)))
                .andExpect(status().isOk());
        // Senha não é mais aceita (campo desconhecido) e o e-mail também não.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"grower_apelido","password":"senha-bem-longa-123"}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"apelido@example.org","accessKey":"%s"}""".formatted(key)))
                .andExpect(status().isBadRequest());
        // A chave é de uma conta só: com outro apelido não entra.
        register("grower_outro");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("grower_outro", key)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginFailuresAreIndistinguishable() throws Exception {
        String key = register("grower_tres");
        String wrongKey = key.substring(0, 49) + (key.endsWith("A") ? "B" : "A");

        String wrong = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("grower_tres", wrongKey)))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String malformed = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("grower_tres", "senha-qualquer")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownUser = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("ninguem_aqui", key)))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(wrong).get("detail"))
                .isEqualTo(json.readTree(unknownUser).get("detail"))
                .isEqualTo(json.readTree(malformed).get("detail"));
    }

    @Test
    void legacyAccountLogsInOnceWithItsPasswordAndReceivesAnAccessKey() throws Exception {
        register("grower_antigo");
        // Conta como era antes da chave de acesso: só a senha (Argon2) guardada.
        String password = "senha-antiga-bem-longa";
        jdbc.update("update users set legacy_password_hash = ?, access_key_hash = null where username = 'grower_antigo'",
                passwordHasher.hash(password).value());

        mvc.perform(post("/api/v1/auth/legacy-login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"grower_antigo","password":"errada-errada-errada"}"""))
                .andExpect(status().isUnauthorized());
        MvcResult migrated = mvc.perform(post("/api/v1/auth/legacy-login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"grower_antigo","password":"%s"}""".formatted(password)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn();
        String key = body(migrated).get("accessKey").asText();
        assertThat(key).startsWith("wd_sec_");

        // A senha foi apagada: o segundo login com ela falha; a chave nova funciona.
        assertThat(jdbc.queryForObject("select legacy_password_hash from users where username = 'grower_antigo'",
                String.class)).isNull();
        mvc.perform(post("/api/v1/auth/legacy-login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"grower_antigo","password":"%s"}""".formatted(password)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("grower_antigo", key)))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsUnknownFieldsInPayload() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"grower_quatro","recoveryVerifier":"%s","acceptedTermsVersion":"1.0","role":"ADMIN"}""".formatted(VERIFIER)))
                .andExpect(status().isBadRequest());
    }

    /** @return a chave de acesso devolvida no cadastro */
    private String register(String username) throws Exception {
        return body(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessKey").asText();
    }

    private static String diaryJson() {
        return """
                {"title":"Tenda 60x60","strainName":"Gorilla Glue #4","breeder":"GG Strains",
                 "floweringType":"PHOTOPERIOD","seedSex":"FEMINIZED","medium":"LIVING_SOIL"}""";
    }
}
