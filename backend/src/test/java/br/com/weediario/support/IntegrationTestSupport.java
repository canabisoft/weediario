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
import org.springframework.test.web.servlet.ResultActions;
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
        "weediario.treatment.encryption-key=dGVzdC1rZXktZm9yLWhlYWx0aC1kYXRhLTMyYnl0ZXM=",
        "spring.datasource.username=",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
@Import(EmbeddedPostgresConfig.class)
public abstract class IntegrationTestSupport {

    /** Versão vigente dos Termos de Uso (o cadastro exige o aceite). */
    protected static final String TERMS = "1.0";
    /** Derivado de uma frase de recuperação (no app ele é calculado no navegador; as palavras nunca chegam aqui). */
    protected static final String VERIFIER = "a1".repeat(64);

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected JdbcTemplate jdbc;

    /** @param accessKey chave de acesso devolvida no cadastro (o servidor só guarda o hash dela) */
    public record Session(String username, String accessToken, String accessKey) {
        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    /** Cadastro já devolve sessão aberta e a chave de acesso. */
    protected Session newGrower() throws Exception {
        String username = "g_" + randomLetters(12);
        MvcResult registered = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = body(registered);
        return new Session(username, response.get("accessToken").asText(), response.get("accessKey").asText());
    }

    /** Grower promovido a moderador; o novo login traz o papel no access token. */
    protected Session moderator() throws Exception {
        Session session = newGrower();
        jdbc.update("update users set role = 'MODERATOR' where username = ?", session.username());
        return relogin(session);
    }

    /** Login de novo com a chave de acesso (pega papel e aceite de termos atualizados no token). */
    protected Session relogin(Session session) throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(session.username(), session.accessKey())))
                .andExpect(status().isOk())
                .andReturn();
        return new Session(session.username(), body(login).get("accessToken").asText(), session.accessKey());
    }

    protected static String loginJson(String username, String accessKey) {
        return """
                {"login":"%s","accessKey":"%s"}""".formatted(username, accessKey);
    }

    /** Cadastro mínimo: apelido, derivado da frase de recuperação e aceite dos termos (sem e-mail nem senha). */
    protected static String registerJson(String username) {
        return """
                {"username":"%s","recoveryVerifier":"%s","acceptedTermsVersion":"%s"}"""
                .formatted(username, VERIFIER, TERMS);
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

    /** Denúncia pelo endpoint unificado de moderação. */
    protected ResultActions report(Session reporter, String targetType, Object targetId, String reason) throws Exception {
        return mvc.perform(authed(post("/api/v1/reports"), reporter).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"targetType":"%s","targetId":"%s","reason":"%s"}""".formatted(targetType, targetId, reason)));
    }

    protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request, Session session) {
        return request.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    /**
     * Apelido aleatório só com letras: um apelido com 10 ou 11 dígitos seguidos tem cara de telefone/CPF e é
     * recusado pelo filtro anti-comércio (de propósito).
     */
    protected static String randomLetters(int length) {
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        StringBuilder letters = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            letters.append((char) ('a' + random.nextInt(26)));
        }
        return letters.toString();
    }
}
