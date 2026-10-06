package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cadastro só com a solução de um desafio anti-robô válido, que vale uma vez só. Tudo é conferido antes do Argon2.
 */
class RegistrationChallengeIntegrationTest extends IntegrationTestSupport {

    @Test
    void challengeIsNotCachedAndHasTheAltchaShape() throws Exception {
        JsonNode challenge = body(mvc.perform(get("/api/v1/auth/register-challenge"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn());
        assertThat(challenge.get("algorithm").asText()).isEqualTo("SHA-256");
        assertThat(challenge.get("challenge").asText()).hasSize(64);
        assertThat(challenge.get("salt").asText()).contains("?expires=");
        assertThat(challenge.get("maxNumber").asLong()).isPositive();
    }

    @Test
    void registrationWithoutAValidSolutionIsRefused() throws Exception {
        assertRefused(withChallenge(null));

        ObjectNode wrongNumber = (ObjectNode) json.readTree(challengeJson());
        wrongNumber.put("number", wrongNumber.get("number").asLong() + 1);
        assertRefused(withChallenge(wrongNumber));

        ObjectNode forged = (ObjectNode) json.readTree(challengeJson());
        forged.put("signature", "0".repeat(64));
        assertRefused(withChallenge(forged));
    }

    @Test
    void aSolutionIsGoodForOneRegistrationOnly() throws Exception {
        String solution = challengeJson();
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(withChallenge(json.readTree(solution))))
                .andExpect(status().isCreated());
        assertRefused(withChallenge(json.readTree(solution)));
    }

    private void assertRefused(String body) throws Exception {
        String username = json.readTree(body).get("username").asText();
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("select count(*) from users where username = ?", Long.class, username)).isZero();
    }

    private String withChallenge(JsonNode challenge) throws Exception {
        ObjectNode body = (ObjectNode) json.readTree(registerJson("c_" + randomLetters(12)));
        body.set("challenge", challenge);
        return json.writeValueAsString(body);
    }
}
