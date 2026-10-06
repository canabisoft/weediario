package br.com.weediario;

import br.com.weediario.shared.infrastructure.observability.SecuritySignals;
import br.com.weediario.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Trocar a senha encerra as outras sessões na hora, inclusive o access token que ainda teria minutos de vida: quem
 * tinha roubado o token perde o acesso no mesmo instante. A sessão aberta na resposta da troca segue valendo.
 */
class SessionRevocationIntegrationTest extends IntegrationTestSupport {

    private static final String NEW_PASSWORD = "estufa nova no quintal";

    @Autowired
    private SecuritySignals signals;

    @Test
    void changingThePasswordKillsAccessTokensOfOtherSessions() throws Exception {
        Session phone = newGrower();
        Session stolen = relogin(phone);
        mvc.perform(authed(get("/api/v1/me"), stolen)).andExpect(status().isOk());

        JsonNode changed = body(mvc.perform(authed(put("/api/v1/me/password"), phone)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}""".formatted(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn());
        Session renewed = new Session(phone.username(), changed.get("accessToken").asText(), NEW_PASSWORD);
        long revokedBefore = signals.count(SecuritySignals.Signal.SESSION_REVOKED);

        mvc.perform(authed(get("/api/v1/me"), stolen))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
        mvc.perform(authed(get("/api/v1/me"), phone))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
        mvc.perform(authed(get("/api/v1/me"), renewed)).andExpect(status().isOk());
        // Os dois tokens recusados aparecem no resumo de segurança (só a contagem, sem quem).
        assertThat(signals.count(SecuritySignals.Signal.SESSION_REVOKED) - revokedBefore).isEqualTo(2);
        // E quem entra depois, com a senha nova, também.
        mvc.perform(authed(get("/api/v1/me"), relogin(renewed))).andExpect(status().isOk());
    }
}
