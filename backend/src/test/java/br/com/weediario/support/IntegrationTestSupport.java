package br.com.weediario.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de integração: aplicação inteira + PostgreSQL embarcado. O contexto é compartilhado entre
 * as classes de teste, então cada teste cria usuários com nomes únicos.
 */
@SpringBootTest(properties = {
        "weediario.security.jwt.secret=dGVzdC1zZWNyZXQtd2l0aC1hdC1sZWFzdC0yNTYtYml0cy1vZi1lbnRyb3B5IQ==",
        "weediario.media.storage-path=target/test-media",
        "weediario.social.pseudonym-key=cHNldWRvbnltLWtleS1mb3ItdGVzdHMtb25seS0yNTYtYml0cyE=",
        "spring.datasource.username=",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
@Import(EmbeddedPostgresConfig.class)
public abstract class IntegrationTestSupport {

    protected static final String PASSWORD = "senha-bem-longa-123";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected JdbcTemplate jdbc;

    public record Session(String username, String accessToken) {
        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    protected Session newGrower() throws Exception {
        String username = "g_" + UUID.randomUUID().toString().substring(0, 12).replace("-", "");
        String email = username + "@example.org";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s","password":"%s"}""".formatted(username, email, PASSWORD)))
                .andExpect(status().isCreated());
        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return new Session(username, body(login).get("accessToken").asText());
    }

    /** Grower promovido a moderador; o novo login traz o papel no access token. */
    protected Session moderator() throws Exception {
        Session session = newGrower();
        jdbc.update("update users set role = 'MODERATOR' where username = ?", session.username());
        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s@example.org","password":"%s"}""".formatted(session.username(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return new Session(session.username(), body(login).get("accessToken").asText());
    }

    protected UUID createDiary(Session session, String strain, String visibility, String startedOn) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/diaries").header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Tenda de teste","strainName":"%s","breeder":"Banco Teste",
                                 "floweringType":"PHOTOPERIOD","seedSex":"FEMINIZED","medium":"LIVING_SOIL",
                                 "visibility":"%s","startedOn":"%s"}""".formatted(strain, visibility, startedOn)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(body(result).get("id").asText());
    }

    protected UUID uploadPhoto(Session session, byte[] content) throws Exception {
        MvcResult result = mvc.perform(multipart("/api/v1/media/images")
                        .file(new MockMultipartFile("file", "IMG_2041.jpg", "image/jpeg", content))
                        .header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(body(result).get("id").asText());
    }

    protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request, Session session) {
        return request.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }
}
