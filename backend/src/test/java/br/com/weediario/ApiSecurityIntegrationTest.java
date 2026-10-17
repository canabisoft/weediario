package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import br.com.weediario.support.TestImages;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regressão das garantias validadas no pentest de caixa branca + dinâmico (OWASP API Top 10): autorização por objeto
 * (BOLA/IDOR), integridade do JWT, autorização de função (BFLA), força bruta, mass assignment, CORS e upload de
 * imagem. A isolação de foto e de dados de tratamento entre contas é coberta em
 * {@link PhotoAndTimelineIntegrationTest} e {@link TreatmentIntegrationTest}; aqui ficam os fluxos restantes.
 */
class ApiSecurityIntegrationTest extends IntegrationTestSupport {

    private static final String HEALTH_TERMS = "2026-09";

    // --- API1: BOLA / IDOR em diário privado -------------------------------------------------

    @Test
    void privateDiaryIsInvisibleAndImmutableToOtherAccounts() throws Exception {
        Session owner = newGrower();
        Session attacker = newGrower();
        UUID diary = createDiary(owner, "White Widow", "PRIVATE", "2026-09-01");

        // Controle positivo: o dono enxerga o próprio diário.
        mvc.perform(authed(get("/api/v1/diaries/{id}", diary), owner)).andExpect(status().isOk());

        // 404 (não 403) para não revelar sequer que o diário existe.
        mvc.perform(authed(get("/api/v1/diaries/{id}", diary), attacker)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/diaries/{id}", diary)).andExpect(status().isNotFound());

        mvc.perform(authed(post("/api/v1/diaries/{id}/updates", diary), attacker)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"VEGETATIVE\",\"note\":\"x\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(authed(put("/api/v1/diaries/{id}/visibility", diary), attacker)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(authed(post("/api/v1/diaries/{id}/complete", diary), attacker)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dryGrams\":100,\"plants\":1}"))
                .andExpect(status().isNotFound());

        // E o diário segue privado e intacto.
        mvc.perform(authed(get("/api/v1/diaries/{id}", diary), owner)).andExpect(status().isOk());
    }

    // --- API1: BOLA / IDOR em paciente (dado de saúde) -------------------------------------

    @Test
    void treatmentPatientIsIsolatedBetweenAccounts() throws Exception {
        Session owner = newGrower();
        Session attacker = newGrower();
        consent(owner);
        consent(attacker);
        UUID patient = createPatient(owner, "Paciente do dono");

        // O intruso não vê o paciente alheio na própria lista...
        JsonNode attackerList = body(mvc.perform(authed(get("/api/v1/me/treatment/patients"), attacker))
                .andExpect(status().isOk()).andReturn());
        assertThat(attackerList.toString()).doesNotContain("Paciente do dono");

        // ...nem edita, apaga ou lê cuidadores conhecendo o id.
        mvc.perform(authed(put("/api/v1/me/treatment/patients/{id}", patient), attacker)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"hack\",\"notes\":\"\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(authed(delete("/api/v1/me/treatment/patients/{id}", patient), attacker))
                .andExpect(status().isNotFound());
        mvc.perform(authed(get("/api/v1/me/treatment/patients/{id}/caregivers", patient), attacker))
                .andExpect(status().isNotFound());

        // O dono continua com o paciente.
        JsonNode ownerList = body(mvc.perform(authed(get("/api/v1/me/treatment/patients"), owner))
                .andExpect(status().isOk()).andReturn());
        assertThat(ownerList.toString()).contains("Paciente do dono");
    }

    // --- API2: integridade do JWT ----------------------------------------------------------

    @Test
    void tamperedTokensAreRejected() throws Exception {
        Session session = newGrower();
        String probe = "/api/v1/me/treatment/consent";

        // Controle positivo: o token íntegro funciona.
        mvc.perform(authed(get(probe), session)).andExpect(status().isOk());

        String[] parts = session.accessToken().split("\\.");
        String header = parts[0];
        String payload = parts[1];
        String signature = parts[2];

        // alg=none, sem assinatura.
        String algNone = b64url("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + payload + ".";
        mvc.perform(get(probe).header(HttpHeaders.AUTHORIZATION, "Bearer " + algNone))
                .andExpect(status().isUnauthorized());

        // Payload adulterado (tenta escalar para ADMIN) com a assinatura original: não bate.
        String escalated = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8)
                .replaceFirst("\"roles\":\\[[^]]*]", "\"roles\":[\"ADMIN\"]");
        String forged = header + "." + b64url(escalated) + "." + signature;
        mvc.perform(get("/api/v1/admin/metrics").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized());

        // Assinatura removida e token sem sentido.
        mvc.perform(get(probe).header(HttpHeaders.AUTHORIZATION, "Bearer " + header + "." + payload + "."))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(probe).header(HttpHeaders.AUTHORIZATION, "Bearer nao.e.um.jwt"))
                .andExpect(status().isUnauthorized());
    }

    // --- API5: BFLA (autorização de função) ------------------------------------------------

    @Test
    void privilegedEndpointsRejectCommonAndAnonymousCallers() throws Exception {
        Session common = newGrower();
        for (String path : new String[]{"/api/v1/admin/metrics", "/api/v1/admin/team",
                "/api/v1/moderation/queue", "/api/v1/moderation/queue/counts"}) {
            mvc.perform(authed(get(path), common)).andExpect(status().isForbidden());
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    // --- API4: força bruta no login --------------------------------------------------------

    @Test
    void repeatedWrongPasswordsLockTheAccountEvenForTheRightOne() throws Exception {
        Session victim = newGrower();

        int firstLock = -1;
        for (int attempt = 1; attempt <= 6; attempt++) {
            int statusCode = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson(victim.username(), "senha errada de proposito")))
                    .andReturn().getResponse().getStatus();
            if (statusCode == 429) {
                firstLock = attempt;
                break;
            }
        }
        assertThat(firstLock).as("bloqueio deve disparar após MAX_FAILURES=5").isEqualTo(6);

        // O bloqueio é por conta: nem a senha certa passa enquanto dura, e vem Retry-After.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(victim.username(), victim.password())))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    // --- API3: mass assignment -------------------------------------------------------------

    @Test
    void registrationRejectsUnknownPrivilegeFields() throws Exception {
        String username = "mass_" + randomLetters(10);
        String payload = registerJson(username).replaceFirst("\\{",
                "{\"role\":\"ADMIN\",\"status\":\"ACTIVE\",");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());
    }

    // --- Config: CORS ----------------------------------------------------------------------

    @Test
    void corsDoesNotAuthorizeUnknownOrigins() throws Exception {
        mvc.perform(options("/api/v1/me/treatment/consent")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    // --- Upload de imagem: só imagem real entra --------------------------------------------

    @Test
    void imageUploadAcceptsRealImagesAndRejectsDisguisedPayloads() throws Exception {
        Session grower = newGrower();

        byte[] png = TestImages.encode(TestImages.twoColor(64, 64), "png");
        mvc.perform(multipart("/api/v1/media/images")
                        .file(new MockMultipartFile("file", "ok.png", "image/png", png))
                        .header(HttpHeaders.AUTHORIZATION, grower.bearer()))
                .andExpect(status().isCreated());

        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        rejectUpload(grower, "x.svg", "image/svg+xml", svg);

        rejectUpload(grower, "fake.jpg", "image/jpeg", "isto nao e uma imagem".getBytes(StandardCharsets.UTF_8));

        // Bomba de pixels: cabeçalho válido anunciando 60000x60000, barrada antes de decodificar.
        rejectUpload(grower, "bomb.png", "image/png", pngHeaderOnly(60000, 60000));
    }

    private void rejectUpload(Session grower, String name, String contentType, byte[] content) throws Exception {
        mvc.perform(multipart("/api/v1/media/images")
                        .file(new MockMultipartFile("file", name, contentType, content))
                        .header(HttpHeaders.AUTHORIZATION, grower.bearer()))
                .andExpect(status().isUnprocessableEntity());
    }

    // --- helpers ---------------------------------------------------------------------------

    private void consent(Session session) throws Exception {
        mvc.perform(authed(put("/api/v1/me/treatment/consent"), session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termsVersion\":\"%s\"}".formatted(HEALTH_TERMS)))
                .andExpect(status().isOk());
    }

    private UUID createPatient(Session owner, String name) throws Exception {
        MvcResult result = mvc.perform(authed(post("/api/v1/me/treatment/patients"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"notes\":\"\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(body(result).get("id").asText());
    }

    private static String b64url(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /** PNG com IHDR válido anunciando dimensões gigantes, sem dados de pixel (não aloca memória real). */
    private static byte[] pngHeaderOnly(int width, int height) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        byte[] ihdr = java.nio.ByteBuffer.allocate(13).putInt(width).putInt(height)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0).array();
        writeChunk(out, "IHDR", ihdr);
        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void writeChunk(java.io.ByteArrayOutputStream out, String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeBytes(java.nio.ByteBuffer.allocate(4).putInt(data.length).array());
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        out.writeBytes(java.nio.ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }
}
