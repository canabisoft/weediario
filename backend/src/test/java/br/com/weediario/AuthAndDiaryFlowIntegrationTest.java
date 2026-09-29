package br.com.weediario;

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
        register("grower_um", "um@example.org");

        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"UM@example.org","password":"senha-bem-longa-123"}"""))
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

        // ...e reapresentar o antigo revoga a família inteira, inclusive o token novo.
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, refreshToken)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, rotated)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesSession() throws Exception {
        register("grower_dois", "dois@example.org");
        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dois@example.org","password":"senha-bem-longa-123"}"""))
                .andReturn();
        Cookie refresh = new Cookie(COOKIE, login.getResponse().getCookie(COOKIE).getValue());

        mvc.perform(post("/api/v1/auth/logout").cookie(refresh)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginFailuresAreIndistinguishable() throws Exception {
        register("grower_tres", "tres@example.org");

        String wrongPassword = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"tres@example.org","password":"senha-errada-errada"}"""))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownEmail = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ninguem@example.org","password":"senha-errada-errada"}"""))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(wrongPassword).get("detail"))
                .isEqualTo(json.readTree(unknownEmail).get("detail"));
    }

    @Test
    void rejectsUnknownFieldsInPayload() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"grower_quatro","email":"q@example.org",
                                 "password":"senha-bem-longa-123","role":"ADMIN"}"""))
                .andExpect(status().isBadRequest());
    }

    private void register(String username, String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s","password":"senha-bem-longa-123"}"""
                                .formatted(username, email)))
                .andExpect(status().isCreated());
    }

    private static String diaryJson() {
        return """
                {"title":"Tenda 60x60","strainName":"Gorilla Glue #4","breeder":"GG Strains",
                 "floweringType":"PHOTOPERIOD","seedSex":"FEMINIZED","medium":"LIVING_SOIL"}""";
    }
}
