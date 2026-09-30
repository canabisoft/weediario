package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GrowerProfileIntegrationTest extends IntegrationTestSupport {

    private static final String SETUP = """
            {"name":"%s","environment":"INDOOR","tentWidthCm":80,"tentDepthCm":80,"tentHeightCm":160,
             "lighting":"LED Quantum Board","lightWattage":240,"medium":"LIVING_SOIL"}""";

    @Test
    void profileShowsHarvestsPublicSetupsAndReviews() throws Exception {
        Session grower = newGrower();
        String publicSetup = setup(grower, "Tenda da sala");
        String privateSetup = setup(grower, "Tenda secreta");
        setup(grower, "Setup nunca usado");

        String first = diaryWithSetup(grower, publicSetup, "PUBLIC", 100);
        diaryWithSetup(grower, publicSetup, "PUBLIC", 10);
        diaryWithSetup(grower, privateSetup, "PRIVATE", 5);

        mvc.perform(authed(post("/api/v1/diaries/{id}/complete", first), grower)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"dryGrams":300,"plants":2}"""))
                .andExpect(status().isOk());
        mvc.perform(authed(post("/api/v1/diaries/{id}/review", first), grower)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"vigor":5,"pestResistance":4,"easeOfGrowth":4,"yield":5,"terpeneProfile":5,
                                 "report":"Planta vigorosa, aroma doce e colheita farta."}"""))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/growers/{u}", grower.username()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diaries").value(2))
                .andExpect(jsonPath("$.harvests").value(1))
                .andExpect(jsonPath("$.gramsPerPlant").value(150.0));

        // Só aparece o setup já visível em diário público, uma vez só, com a contagem de diários.
        JsonNode setups = body(mvc.perform(get("/api/v1/growers/{u}/setups", grower.username()))
                .andExpect(status().isOk()).andReturn());
        assertThat(setups).hasSize(1);
        assertThat(setups.get(0).get("setup").get("name").asText()).isEqualTo("Tenda da sala");
        assertThat(setups.get(0).get("diaries").asInt()).isEqualTo(2);

        mvc.perform(get("/api/v1/growers/{u}/reviews", grower.username()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].strain").value("Perfil Kush"))
                .andExpect(jsonPath("$.items[0].diaryId").value(first))
                .andExpect(jsonPath("$.items[0].overall").value(4.6));
    }

    @Test
    void unknownGrowerHasNoSetupsOrReviews() throws Exception {
        mvc.perform(get("/api/v1/growers/{u}/setups", "ninguem_aqui")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/growers/{u}/reviews", "ninguem_aqui")).andExpect(jsonPath("$.totalItems").value(0));
    }

    private String setup(Session grower, String name) throws Exception {
        return body(mvc.perform(authed(post("/api/v1/me/setups"), grower)
                        .contentType(MediaType.APPLICATION_JSON).content(SETUP.formatted(name)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private String diaryWithSetup(Session grower, String setupId, String visibility, int daysAgo) throws Exception {
        return body(mvc.perform(authed(post("/api/v1/diaries"), grower)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Ciclo","strainName":"Perfil Kush","floweringType":"PHOTOPERIOD",
                                 "seedSex":"FEMINIZED","setupId":"%s","visibility":"%s","startedOn":"%s"}"""
                                .formatted(setupId, visibility, LocalDate.now().minusDays(daysAgo))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }
}
