package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SetupIntegrationTest extends IntegrationTestSupport {

    private static final String SETUP = """
            {"name":"%s","environment":"INDOOR","tentWidthCm":80,"tentDepthCm":80,"tentHeightCm":160,
             "lighting":"LED Quantum Board","lightWattage":240,"medium":"LIVING_SOIL",
             "nutrients":"Orgânico","ventilation":"Exaustor 100mm"}""";

    @Test
    void diaryStartedFromSetupKeepsFrozenCopy() throws Exception {
        Session grower = newGrower();
        String setupId = body(mvc.perform(authed(post("/api/v1/me/setups"), grower)
                        .contentType(MediaType.APPLICATION_JSON).content(SETUP.formatted("Tenda 80x80")))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();

        // Sem meio de cultivo no pedido: herdado do setup.
        JsonNode diary = body(mvc.perform(authed(post("/api/v1/diaries"), grower)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Ciclo 3","strainName":"Gelato","floweringType":"PHOTOPERIOD",
                                 "seedSex":"FEMINIZED","setupId":"%s","startedOn":"%s"}"""
                                .formatted(setupId, LocalDate.now())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.medium").value("LIVING_SOIL"))
                .andExpect(jsonPath("$.setup.spec.lightWattage").value(240))
                .andReturn());
        String diaryId = diary.get("id").asText();

        // Editar e apagar o setup não mexe no que ficou registrado no diário.
        mvc.perform(authed(put("/api/v1/me/setups/{id}", setupId), grower)
                        .contentType(MediaType.APPLICATION_JSON).content(SETUP.formatted("Tenda nova")
                                .replace("\"lightWattage\":240", "\"lightWattage\":480")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spec.lightWattage").value(480));
        mvc.perform(authed(delete("/api/v1/me/setups/{id}", setupId), grower)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/diaries/{id}", diaryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setup.name").value("Tenda 80x80"))
                .andExpect(jsonPath("$.setup.spec.lightWattage").value(240));
    }

    @Test
    void setupsArePrivateToTheirOwner() throws Exception {
        Session owner = newGrower();
        Session other = newGrower();
        String setupId = body(mvc.perform(authed(post("/api/v1/me/setups"), owner)
                        .contentType(MediaType.APPLICATION_JSON).content(SETUP.formatted("Meu setup")))
                .andReturn()).get("id").asText();

        JsonNode othersList = body(mvc.perform(authed(get("/api/v1/me/setups"), other)).andReturn());
        assertThat(othersList).isEmpty();
        mvc.perform(authed(delete("/api/v1/me/setups/{id}", setupId), other)).andExpect(status().isNotFound());
        mvc.perform(authed(post("/api/v1/diaries"), other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Roubado","strainName":"X","floweringType":"PHOTOPERIOD",
                                 "seedSex":"FEMINIZED","setupId":"%s"}""".formatted(setupId)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/me/setups")).andExpect(status().isUnauthorized());
    }

    @Test
    void mediumIsRequiredWithoutSetup() throws Exception {
        Session grower = newGrower();

        mvc.perform(authed(post("/api/v1/diaries"), grower)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Sem meio","strainName":"X","floweringType":"PHOTOPERIOD","seedSex":"FEMINIZED"}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Meio de cultivo é obrigatório"));
    }

    @Test
    void rejectsImpossibleDimensions() throws Exception {
        Session grower = newGrower();

        mvc.perform(authed(post("/api/v1/me/setups"), grower)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SETUP.formatted("Gigante").replace("\"tentWidthCm\":80", "\"tentWidthCm\":99999")))
                .andExpect(status().isBadRequest());
    }
}
