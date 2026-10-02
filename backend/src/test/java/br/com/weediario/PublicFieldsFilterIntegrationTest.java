package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Achado da auditoria: campos públicos que escapavam do filtro anti-comércio (apelido e textos do setup, que
 * aparecem no perfil e em cada comentário).
 */
class PublicFieldsFilterIntegrationTest extends IntegrationTestSupport {

    private static final String SETUP = """
            {"name":"%s","environment":"INDOOR","lighting":"LED Quantum Board","lightWattage":240,
             "medium":"LIVING_SOIL","notes":"%s"}""";

    @Test
    void usernameCannotCarryContactData() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("zap11987654321")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("growerdo_quintal")))
                .andExpect(status().isCreated());
    }

    @Test
    void setupTextsArePublicAndFiltered() throws Exception {
        Session grower = newGrower();
        mvc.perform(authed(post("/api/v1/me/setups"), grower).contentType(MediaType.APPLICATION_JSON)
                        .content(SETUP.formatted("Tenda 80x80", "chama no zap 11 98765-4321")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(authed(post("/api/v1/me/setups"), grower).contentType(MediaType.APPLICATION_JSON)
                        .content(SETUP.formatted("Vendo setup R$ 900, entrego", "")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(authed(post("/api/v1/me/setups"), grower).contentType(MediaType.APPLICATION_JSON)
                        .content(SETUP.formatted("Tenda 80x80", "EC 1.8, pH 6,2, 18/6, LED de 240 W")))
                .andExpect(status().isCreated());
    }
}
