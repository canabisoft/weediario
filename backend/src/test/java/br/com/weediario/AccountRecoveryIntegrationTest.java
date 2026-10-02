package br.com.weediario;

import br.com.weediario.support.IntegrationTestSupport;
import br.com.weediario.support.RecordingAccountMailer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Apelido + senha, e-mail opcional e duas formas de recuperar: a frase de 12 palavras (toda conta tem; só o derivado
 * chega ao servidor) e o link pelo e-mail confirmado.
 */
class AccountRecoveryIntegrationTest extends IntegrationTestSupport {

    private static final String COOKIE = "__Secure-wd_rt";
    private static final String OTHER_VERIFIER = "b2".repeat(64);
    private static final String THIRD_VERIFIER = "c3".repeat(64);
    private static final String NEW_PASSWORD = "outra frase bem comprida";

    // --- e-mail opcional ---------------------------------------------------------------------------------------

    @Test
    void emailIsOptionalEncryptedAndOnlyUsableAfterConfirmation() throws Exception {
        String username = "g_" + randomLetters(12);
        String email = username + "@exemplo.com.br";
        MvcResult registered = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, email)))
                .andExpect(status().isCreated())
                .andReturn();
        Session grower = new Session(username, body(registered).get("accessToken").asText(), PASSWORD);

        // A tabela de usuários não tem e-mail; o endereço fica cifrado em outra tabela, sem índice.
        List<String> columns = jdbc.queryForList("""
                select column_name from information_schema.columns
                 where table_schema = 'public' and table_name = 'users'""", String.class);
        assertThat(columns).doesNotContain("email").contains("password_hash", "recovery_hash");
        byte[] stored = jdbc.queryForObject("""
                select e.email_enc from account_emails e join users u on u.id = e.user_id where u.username = ?""",
                byte[].class, username);
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain(email, username);

        // A tela só vê o endereço mascarado; antes da confirmação ele não serve para recuperar.
        mvc.perform(authed(get("/api/v1/me/security"), grower))
                .andExpect(jsonPath("$.email").value(org.hamcrest.Matchers.containsString("***")))
                .andExpect(jsonPath("$.emailVerified").value(false));
        mvc.perform(post("/api/v1/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                        .content(resetJson(username)))
                .andExpect(status().isAccepted());
        assertThat(mail.last(RecordingAccountMailer.Kind.RESET_PASSWORD, username)).isEmpty();

        String token = mail.last(RecordingAccountMailer.Kind.VERIFY_EMAIL, username).orElseThrow().token();
        mvc.perform(post("/api/v1/auth/email/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(tokenJson(token)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/email/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(tokenJson(token)))
                .andExpect(status().isUnauthorized());
        mvc.perform(authed(get("/api/v1/me/security"), grower))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.events[0].kind").value("EMAIL_VERIFIED"));
    }

    @Test
    void registrationNeverRevealsWhetherAnEmailAlreadyHasAnAccount() throws Exception {
        String shared = "mesmo_" + randomLetters(6) + "@exemplo.com";
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(registerJson("g_" + randomLetters(12), shared)))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void passwordResetByEmailIsSingleUseRevokesSessionsAndIsRateLimited() throws Exception {
        Session grower = growerWithVerifiedEmail();
        Cookie oldRefresh = refreshCookie(login(grower.username(), PASSWORD));

        // Mesma resposta para quem não existe.
        mvc.perform(post("/api/v1/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                        .content(resetJson("ninguem_aqui")))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                        .content(resetJson(grower.username())))
                .andExpect(status().isAccepted());
        String token = mail.last(RecordingAccountMailer.Kind.RESET_PASSWORD, grower.username()).orElseThrow().token();

        // Pedir de novo logo em seguida não manda outro e-mail (ninguém lota a caixa de entrada de outra pessoa).
        mvc.perform(post("/api/v1/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                        .content(resetJson(grower.username())))
                .andExpect(status().isAccepted());
        assertThat(mail.last(RecordingAccountMailer.Kind.RESET_PASSWORD, grower.username()).orElseThrow().token())
                .isEqualTo(token);

        mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(token, "curta")))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(token, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(token, "mais uma senha qualquer")))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/refresh").cookie(oldRefresh)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(grower.username(), PASSWORD)))
                .andExpect(status().isUnauthorized());
        login(grower.username(), NEW_PASSWORD);
        assertThat(mail.last(RecordingAccountMailer.Kind.SECURITY_EVENT, grower.username()).orElseThrow().event())
                .hasToString("PASSWORD_RESET_BY_EMAIL");
    }

    // --- frase de recuperação ----------------------------------------------------------------------------------

    @Test
    void phraseRecoverySetsNewPasswordRotatesThePhraseAndRevokesSessions() throws Exception {
        Session grower = newGrower();
        Cookie oldRefresh = refreshCookie(login(grower.username(), PASSWORD));

        // A frase nova não pode ser a mesma que acabou de ser digitada.
        mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content(recoverJson(grower.username(), VERIFIER, NEW_PASSWORD, VERIFIER)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content(recoverJson(grower.username().toUpperCase(), VERIFIER, NEW_PASSWORD, OTHER_VERIFIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mvc.perform(post("/api/v1/auth/refresh").cookie(oldRefresh)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(grower.username(), PASSWORD)))
                .andExpect(status().isUnauthorized());
        login(grower.username(), NEW_PASSWORD);
        // A frase antiga deixou de valer; a nova funciona.
        mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content(recoverJson(grower.username(), VERIFIER, PASSWORD, THIRD_VERIFIER)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content(recoverJson(grower.username(), OTHER_VERIFIER, PASSWORD, THIRD_VERIFIER)))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPhraseAndUnknownUserGetTheSameAnswerAndPasswordGuessingNeverBlocksRecovery() throws Exception {
        Session grower = newGrower();

        String wrongPhrase = mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content(recoverJson(grower.username(), OTHER_VERIFIER, NEW_PASSWORD, THIRD_VERIFIER)))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownUser = mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content(recoverJson("ninguem_aqui", VERIFIER, NEW_PASSWORD, THIRD_VERIFIER)))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(wrongPhrase).get("detail")).isEqualTo(json.readTree(unknownUser).get("detail"));

        // Alguém errando a senha de propósito só atrasa o login; a recuperação nunca é bloqueada.
        for (int i = 0; i < 8; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson(grower.username(), "chutando a senha " + i)));
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(grower.username(), PASSWORD)))
                .andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content(recoverJson(grower.username(), VERIFIER, NEW_PASSWORD, OTHER_VERIFIER)))
                .andExpect(status().isOk());
    }

    // --- mudanças com a sessão aberta exigem a senha atual -----------------------------------------------------

    @Test
    void changingPasswordRequiresTheCurrentOneAndDropsOtherSessions() throws Exception {
        Session grower = newGrower();
        Cookie otherDevice = refreshCookie(login(grower.username(), PASSWORD));

        mvc.perform(authed(put("/api/v1/me/password"), grower).contentType(MediaType.APPLICATION_JSON).content("""
                        {"currentPassword":"nao e essa nao","newPassword":"%s"}""".formatted(NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(authed(put("/api/v1/me/password"), grower).contentType(MediaType.APPLICATION_JSON).content("""
                        {"currentPassword":"%s","newPassword":"%s"}""".formatted(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mvc.perform(post("/api/v1/auth/refresh").cookie(otherDevice)).andExpect(status().isUnauthorized());
        login(grower.username(), NEW_PASSWORD);
    }

    @Test
    void changingOrRemovingTheEmailRequiresThePasswordAndWarnsTheOldAddress() throws Exception {
        Session grower = growerWithVerifiedEmail();
        String oldAddress = mail.last(RecordingAccountMailer.Kind.VERIFY_EMAIL, grower.username()).orElseThrow().to();

        mvc.perform(authed(put("/api/v1/me/email"), grower).contentType(MediaType.APPLICATION_JSON).content("""
                        {"currentPassword":"errada errada","email":"novo@exemplo.com"}"""))
                .andExpect(status().isUnauthorized());
        mvc.perform(authed(put("/api/v1/me/email"), grower).contentType(MediaType.APPLICATION_JSON).content("""
                        {"currentPassword":"%s","email":"novo@exemplo.com"}""".formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(false));

        // O endereço antigo (confirmado) é quem recebe o aviso: se foi um invasor que trocou, o dono fica sabendo.
        RecordingAccountMailer.Sent warning = mail.last(RecordingAccountMailer.Kind.SECURITY_EVENT, grower.username())
                .orElseThrow();
        assertThat(warning.to()).isEqualTo(oldAddress);
        assertThat(warning.event()).hasToString("EMAIL_CHANGED");
        assertThat(mail.last(RecordingAccountMailer.Kind.VERIFY_EMAIL, grower.username()).orElseThrow().to())
                .isEqualTo("novo@exemplo.com");

        mvc.perform(authed(put("/api/v1/me/email"), grower).contentType(MediaType.APPLICATION_JSON).content("""
                        {"currentPassword":"%s","email":""}""".formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist());
        assertThat(jdbc.queryForObject("""
                select count(*) from account_emails e join users u on u.id = e.user_id where u.username = ?""",
                Integer.class, grower.username())).isZero();
    }

    @Test
    void phraseIsGeneratedOrChangedOnlyWithTheCurrentPassword() throws Exception {
        Session grower = newGrower();
        jdbc.update("update users set recovery_hash = null where username = ?", grower.username());

        mvc.perform(authed(get("/api/v1/me"), grower)).andExpect(jsonPath("$.hasRecoveryPhrase").value(false));
        mvc.perform(authed(put("/api/v1/me/recovery"), grower).contentType(MediaType.APPLICATION_JSON).content("""
                        {"recoveryVerifier":"%s","currentPassword":"senha errada aqui"}""".formatted(OTHER_VERIFIER)))
                .andExpect(status().isUnauthorized());
        mvc.perform(authed(put("/api/v1/me/recovery"), grower).contentType(MediaType.APPLICATION_JSON).content("""
                        {"recoveryVerifier":"%s","currentPassword":"%s"}""".formatted(OTHER_VERIFIER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasRecoveryPhrase").value(true));

        mvc.perform(authed(get("/api/v1/me/security"), grower))
                .andExpect(jsonPath("$.events[0].kind").value("RECOVERY_PHRASE_CHANGED"))
                .andExpect(jsonPath("$.events[0].ip").doesNotExist());
    }

    @Test
    void securitySettingsWorkEvenBeforeAcceptingNewTerms() throws Exception {
        Session grower = newGrower();
        jdbc.update("update users set terms_version = null, terms_accepted_at = null where username = ?",
                grower.username());
        Session legacy = relogin(grower);

        mvc.perform(authed(put("/api/v1/me/recovery"), legacy).contentType(MediaType.APPLICATION_JSON).content("""
                        {"recoveryVerifier":"%s","currentPassword":"%s"}""".formatted(OTHER_VERIFIER, PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(authed(get("/api/v1/me/security"), legacy)).andExpect(status().isOk());
    }

    private Session growerWithVerifiedEmail() throws Exception {
        String username = "g_" + randomLetters(12);
        MvcResult registered = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, username + "@exemplo.com")))
                .andExpect(status().isCreated())
                .andReturn();
        String token = mail.last(RecordingAccountMailer.Kind.VERIFY_EMAIL, username).orElseThrow().token();
        mvc.perform(post("/api/v1/auth/email/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(tokenJson(token)))
                .andExpect(status().isNoContent());
        return new Session(username, body(registered).get("accessToken").asText(), PASSWORD);
    }

    private MvcResult login(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static Cookie refreshCookie(MvcResult result) {
        return new Cookie(COOKIE, result.getResponse().getCookie(COOKIE).getValue());
    }

    private static String recoverJson(String username, String verifier, String newPassword, String newVerifier) {
        return """
                {"username":"%s","recoveryVerifier":"%s","newPassword":"%s","newRecoveryVerifier":"%s"}"""
                .formatted(username, verifier, newPassword, newVerifier);
    }

    private static String resetJson(String username) {
        return """
                {"username":"%s"}""".formatted(username);
    }

    private static String confirmJson(String token, String newPassword) {
        return """
                {"token":"%s","newPassword":"%s"}""".formatted(token, newPassword);
    }

    private static String tokenJson(String token) {
        return """
                {"token":"%s"}""".formatted(token);
    }
}
