package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.oneOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Leitura dos sinais de segurança pelo monitor externo: só com o token dele, e sem nada que identifique alguém. */
class MonitoringIntegrationTest extends IntegrationTestSupport {

    private static final String PATH = "/api/v1/ops/signals";
    private static final String HEADER = "X-Weediario-Monitor";

    @Test
    void withoutTheMonitorTokenTheRouteDoesNotExist() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isNotFound());
        mvc.perform(get(PATH).header(HEADER, MONITOR_TOKEN + "x")).andExpect(status().isNotFound());
        mvc.perform(get(PATH).header(HEADER, "")).andExpect(status().isNotFound());
    }

    @Test
    void anAccountSessionIsNotTheMonitorToken() throws Exception {
        Session grower = newGrower();
        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, grower.bearer())).andExpect(status().isNotFound());
    }

    @Test
    void theMonitorReadsOnlyFixedNamesAndNumbers() throws Exception {
        mvc.perform(get(PATH).header(HEADER, MONITOR_TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.status").value(oneOf("ok", "alert")))
                .andExpect(jsonPath("$.upSince").isString())
                .andExpect(jsonPath("$.currentHour.from").isString())
                .andExpect(jsonPath("$.currentHour.counts", hasKey("LOGIN_FAILED")))
                .andExpect(jsonPath("$.alerts").isArray());
    }

    @Test
    void onlyReading() throws Exception {
        mvc.perform(post(PATH).header(HEADER, MONITOR_TOKEN)).andExpect(status().is4xxClientError());
    }
}
