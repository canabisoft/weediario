package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TreatmentIntegrationTest extends IntegrationTestSupport {

    private static final String API = "/api/v1/me/treatment";

    @Test
    void nothingIsAccessibleWithoutSpecificConsent() throws Exception {
        Session patient = newGrower();

        mvc.perform(get(API + "/patients")).andExpect(status().isUnauthorized());
        mvc.perform(authed(get(API + "/patients"), patient))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TREATMENT_CONSENT_REQUIRED"));
        mvc.perform(authed(get(API + "/consent"), patient)).andExpect(jsonPath("$.accepted").value(false));

        mvc.perform(authed(put(API + "/consent"), patient).contentType(MediaType.APPLICATION_JSON)
                .content("{\"termsVersion\":\"2020-01\"}")).andExpect(status().isUnprocessableEntity());
        consent(patient);
        mvc.perform(authed(get(API + "/patients"), patient)).andExpect(status().isOk());
    }

    @Test
    void doseIsMeasuredFromTheLabelAndDeductedFromStock() throws Exception {
        Session grower = newGrower();
        consent(grower);
        String patientId = createPatient(grower, "Eu mesmo");
        String pain = symptom(grower, patientId, "Dor lombar", "SCALE");
        String oil = medicine(grower, patientId, """
                {"name":"Óleo 20:1","form":"OIL","origin":"ASSOCIATION","thc":1,"cbd":20,"dropsPerMl":20,
                 "initialQuantity":30,"expiresOn":"2027-06-30"}""");

        // 10 gotas a 20 gotas/ml = 0,5 ml → 10 mg de CBD e 0,5 mg de THC.
        String dose = recordDose(grower, patientId, """
                {"batchId":"%s","amount":10,"unit":"DROP","before":[{"symptomId":"%s","value":7}],
                 "sideEffects":[{"effect":"DROWSINESS","intensity":"MILD"}],"notes":"antes de dormir"}"""
                .formatted(oil, pain));

        JsonNode today = body(mvc.perform(authed(get(API + "/patients/{id}/today", patientId), grower))
                .andExpect(status().isOk()).andReturn());
        assertThat(today.get("cbdMgToday").decimalValue()).isEqualByComparingTo("10");
        assertThat(today.get("thcMgToday").decimalValue()).isEqualByComparingTo("0.5");
        assertThat(today.get("medicines").get(0).get("remaining").decimalValue()).isEqualByComparingTo("29.5");
        assertThat(today.get("medicines").get(0).get("stockUnit").asText()).isEqualTo("ML");
        assertThat(today.get("pendingAfter").get(0).get("doseId").asText()).isEqualTo(dose);
        assertThat(today.get("doses").get(0).get("sideEffects").get(0).get("effect").asText()).isEqualTo("DROWSINESS");
        assertThat(today.get("doses").get(0).get("notes").asText()).isEqualTo("antes de dormir");

        mvc.perform(authed(put(API + "/patients/{p}/doses/{d}/after", patientId, dose), grower)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ratings\":[{\"symptomId\":\"%s\",\"value\":3}]}".formatted(pain)))
                .andExpect(status().isNoContent());
        mvc.perform(authed(get(API + "/patients/{id}/today", patientId), grower))
                .andExpect(jsonPath("$.pendingAfter.length()").value(0));

        // Escala vai de 0 a 10; unidade precisa combinar com a forma; só usos que já aconteceram.
        mvc.perform(authed(put(API + "/patients/{p}/doses/{d}/after", patientId, dose), grower)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ratings\":[{\"symptomId\":\"%s\",\"value\":11}]}".formatted(pain)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(authed(post(API + "/patients/{id}/doses", patientId), grower).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchId\":\"%s\",\"amount\":1,\"unit\":\"G\"}".formatted(oil)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(authed(post(API + "/patients/{id}/doses", patientId), grower).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchId\":\"%s\",\"amount\":5,\"unit\":\"DROP\",\"takenAt\":\"%s\"}"
                                .formatted(oil, Instant.now().plus(2, ChronoUnit.HOURS))))
                .andExpect(status().isUnprocessableEntity());

        // Apagar a dose registrada por engano devolve o estoque.
        mvc.perform(authed(delete(API + "/patients/{p}/doses/{d}", patientId, dose), grower))
                .andExpect(status().isNoContent());
        mvc.perform(authed(get(API + "/patients/{id}/medicines", patientId), grower))
                .andExpect(jsonPath("$[0].remaining").value(30.0));
    }

    @Test
    void flowerTopicalAndCountSymptomsFollowTheirOwnUnits() throws Exception {
        Session grower = newGrower();
        consent(grower);
        String patientId = createPatient(grower, "Paciente");
        String seizures = symptom(grower, patientId, "Crises", "COUNT");
        String flower = medicine(grower, patientId, """
                {"name":"Flor própria","form":"FLOWER","origin":"HOME_GROWN","thc":20,"cbd":1,"initialQuantity":10}""");
        String balm = medicine(grower, patientId, """
                {"name":"Pomada","form":"TOPICAL","initialQuantity":50}""");

        recordDose(grower, patientId, "{\"batchId\":\"%s\",\"amount\":0.5,\"unit\":\"G\"}".formatted(flower));
        recordDose(grower, patientId, "{\"batchId\":\"%s\",\"amount\":1,\"unit\":\"APPLICATION\"}".formatted(balm));
        mvc.perform(authed(post(API + "/patients/{id}/checkins", patientId), grower).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ratings\":[{\"symptomId\":\"%s\",\"value\":2}]}".formatted(seizures)))
                .andExpect(status().isCreated());

        JsonNode timeline = body(mvc.perform(authed(get(API + "/patients/{id}/timeline", patientId), grower))
                .andExpect(status().isOk()).andReturn());
        JsonNode doses = timeline.get("doses");
        JsonNode flowerDose = doses.get(1);
        assertThat(flowerDose.get("route").asText()).isEqualTo("INHALED");
        assertThat(flowerDose.get("thcMg").decimalValue()).isEqualByComparingTo("100"); // 0,5 g a 20%
        assertThat(doses.get(0).has("stockQuantity")).isFalse(); // aplicação de pomada não desconta nem vira mg
        assertThat(timeline.get("checkins").get(0).get("value").asInt()).isEqualTo(2);

        JsonNode series = body(mvc.perform(authed(get(API + "/patients/{id}/series", patientId), grower))
                .andExpect(status().isOk()).andReturn());
        JsonNode lastDay = series.get("days").get(series.get("days").size() - 1);
        assertThat(lastDay.get("doses").asInt()).isEqualTo(2);
        assertThat(lastDay.get("symptoms").get(seizures).decimalValue()).isEqualByComparingTo("2");
        mvc.perform(authed(get(API + "/patients/{id}/medicines", patientId), grower))
                .andExpect(jsonPath("$[?(@.name == 'Pomada')].remaining").value(50.0))
                .andExpect(jsonPath("$[?(@.name == 'Flor própria')].remaining").value(9.5));
    }

    @Test
    void healthTextsAreEncryptedAtRest() throws Exception {
        Session grower = newGrower();
        consent(grower);
        String patientId = createPatient(grower, "Nome Bem Identificavel");
        symptom(grower, patientId, "Enxaqueca cronica", "SCALE");

        byte[] stored = jdbc.queryForObject("select name_enc from treatment_patients where id = ?::uuid", byte[].class,
                patientId);
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("Identificavel");
        assertThat(jdbc.queryForList("select name_enc from treatment_symptoms where patient_id = ?::uuid", byte[].class,
                patientId)).allSatisfy(name -> assertThat(new String(name, StandardCharsets.ISO_8859_1)).doesNotContain("Enxaqueca"));
        mvc.perform(authed(get(API + "/patients"), grower))
                .andExpect(jsonPath("$.patients[0].name").value("Nome Bem Identificavel"));
    }

    @Test
    void caregiverRecordsAfterAcceptingButCannotManageTheProfile() throws Exception {
        Session owner = newGrower();
        Session caregiver = newGrower();
        Session stranger = newGrower();
        consent(owner);
        consent(stranger);
        String patientId = createPatient(owner, "Filho");
        String oil = medicine(owner, patientId, """
                {"name":"Óleo CBD","form":"OIL","cbd":50,"initialQuantity":10}""");

        mvc.perform(authed(post(API + "/patients/{id}/caregivers", patientId), owner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\"}".formatted(caregiver.username()))).andExpect(status().isNoContent());

        // Convite pendente não dá acesso; aceitar exige o consentimento da própria pessoa.
        mvc.perform(authed(post(API + "/invites/{id}", patientId), caregiver)).andExpect(status().isForbidden());
        consent(caregiver);
        mvc.perform(authed(get(API + "/patients/{id}/today", patientId), caregiver)).andExpect(status().isNotFound());
        mvc.perform(authed(get(API + "/patients"), caregiver))
                .andExpect(jsonPath("$.invites[0].patientName").value("Filho"))
                .andExpect(jsonPath("$.invites[0].owner").value(owner.username()));
        mvc.perform(authed(post(API + "/invites/{id}", patientId), caregiver)).andExpect(status().isNoContent());

        recordDose(caregiver, patientId, "{\"batchId\":\"%s\",\"amount\":4,\"unit\":\"DROP\"}".formatted(oil));
        mvc.perform(authed(get(API + "/patients/{id}/timeline", patientId), owner))
                .andExpect(jsonPath("$.doses[0].recordedBy").value(caregiver.username()));
        mvc.perform(authed(get(API + "/patients"), caregiver)).andExpect(jsonPath("$.patients[0].role").value("CAREGIVER"));

        mvc.perform(authed(delete(API + "/patients/{id}", patientId), caregiver)).andExpect(status().isUnprocessableEntity());
        mvc.perform(authed(post(API + "/patients/{id}/caregivers", patientId), caregiver)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\"}".formatted(stranger.username()))).andExpect(status().isUnprocessableEntity());
        mvc.perform(authed(get(API + "/patients/{id}/today", patientId), stranger)).andExpect(status().isNotFound());

        mvc.perform(authed(delete(API + "/invites/{id}", patientId), caregiver)).andExpect(status().isNoContent());
        mvc.perform(authed(get(API + "/patients/{id}/today", patientId), caregiver)).andExpect(status().isNotFound());
    }

    @Test
    void reportIsAServerGeneratedPdfThatOnlyStatesWhatWasRecorded() throws Exception {
        Session grower = newGrower();
        consent(grower);
        String patientId = createPatient(grower, "Paciente do relatório");
        String anxiety = symptom(grower, patientId, "Ansiedade", "SCALE");
        String oil = medicine(grower, patientId, """
                {"name":"Óleo 1:1","form":"OIL","thc":10,"cbd":10,"initialQuantity":30}""");
        String dose = recordDose(grower, patientId, """
                {"batchId":"%s","amount":5,"unit":"DROP","before":[{"symptomId":"%s","value":8}],
                 "sideEffects":[{"effect":"DRY_MOUTH"}]}""".formatted(oil, anxiety));
        mvc.perform(authed(put(API + "/patients/{p}/doses/{d}/after", patientId, dose), grower)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ratings\":[{\"symptomId\":\"%s\",\"value\":4}]}".formatted(anxiety)));

        MvcResult result = mvc.perform(authed(get(API + "/patients/{id}/report", patientId), grower))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn();
        byte[] pdf = result.getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

        PdfReader reader = new PdfReader(pdf);
        StringBuilder text = new StringBuilder();
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            text.append(extractor.getTextFromPage(page)).append('\n');
        }
        String content = text.toString().replaceAll("\\s+", " ");
        assertThat(content)
                .contains("Paciente do relatório", "Óleo 1:1", "Ansiedade", "Boca seca", "8 / 4")
                .contains("não contém prescrição")
                .doesNotContainIgnoringCase("recomendad")
                .doesNotContainIgnoringCase("sugerid");

        Session stranger = newGrower();
        consent(stranger);
        mvc.perform(authed(get(API + "/patients/{id}/report", patientId), stranger)).andExpect(status().isNotFound());
    }

    @Test
    void revokingConsentErasesEverythingAndExportComesFirst() throws Exception {
        Session grower = newGrower();
        consent(grower);
        String patientId = createPatient(grower, "Para apagar");
        String oil = medicine(grower, patientId, "{\"name\":\"Óleo\",\"form\":\"OIL\",\"cbd\":10,\"initialQuantity\":10}");
        recordDose(grower, patientId, "{\"batchId\":\"%s\",\"amount\":2,\"unit\":\"ML\"}".formatted(oil));

        mvc.perform(authed(get(API + "/export"), grower))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(jsonPath("$.patients[0].patient.name").value("Para apagar"))
                .andExpect(jsonPath("$.patients[0].doses[0].cbdMg").value(20.0));

        mvc.perform(authed(delete(API), grower)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from treatment_patients where id = ?::uuid", Long.class, patientId))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from treatment_doses where patient_id = ?::uuid", Long.class,
                patientId)).isZero();
        mvc.perform(authed(get(API + "/consent"), grower)).andExpect(jsonPath("$.accepted").value(false));
    }

    // --------------------------------------------------------------------------------------- helpers

    private void consent(Session session) throws Exception {
        mvc.perform(authed(put(API + "/consent"), session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termsVersion\":\"2026-09\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true));
    }

    private String createPatient(Session session, String name) throws Exception {
        return created(mvc.perform(authed(post(API + "/patients"), session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\"}".formatted(name))).andExpect(status().isCreated()).andReturn());
    }

    private String symptom(Session session, String patientId, String name, String kind) throws Exception {
        return created(mvc.perform(authed(post(API + "/patients/{id}/symptoms", patientId), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"kind\":\"%s\"}".formatted(name, kind)))
                .andExpect(status().isCreated()).andReturn());
    }

    private String medicine(Session session, String patientId, String json) throws Exception {
        return created(mvc.perform(authed(post(API + "/patients/{id}/medicines", patientId), session)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated()).andReturn());
    }

    private String recordDose(Session session, String patientId, String json) throws Exception {
        return created(mvc.perform(authed(post(API + "/patients/{id}/doses", patientId), session)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated()).andReturn());
    }

    private String created(MvcResult result) throws Exception {
        return body(result).get("id").asText();
    }
}
