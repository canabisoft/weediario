package br.com.weediario.identity.presentation.rest;

import br.com.weediario.identity.application.usecase.AccountDataUseCase;
import br.com.weediario.identity.domain.model.Username;
import br.com.weediario.identity.domain.service.PasswordPolicy;
import br.com.weediario.shared.presentation.Viewer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Direitos do titular: baixar os dados (um .zip com {@code dados.json}, as fotos e um LEIA-ME) e apagar a conta.
 * POST nos dois: a senha vai no corpo, nunca na URL.
 */
@RestController
public class AccountDataController {

    private static final String README = """
            Seus dados no weediário
            =======================

            dados.json  tudo o que a sua conta guarda, por seção (conta, diários, fotos, receitas, tratamento...).
            fotos/      as suas fotos, na versão grande, já sem metadados (sem GPS, aparelho ou data).

            Não estão aqui:
            - sua senha e sua frase de recuperação: guardamos só hashes, que não servem para nada fora do app;
            - reações e denúncias: são guardadas só com um pseudônimo por alvo, sem vínculo com a conta.
            """;

    private final AccountDataUseCase accountData;
    private final RefreshTokenCookieFactory cookies;
    private final ObjectMapper json;

    public AccountDataController(AccountDataUseCase accountData, RefreshTokenCookieFactory cookies, ObjectMapper json) {
        this.accountData = accountData;
        this.cookies = cookies;
        this.json = json.copy().enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @PostMapping("/api/v1/me/export")
    public ResponseEntity<StreamingResponseBody> export(@AuthenticationPrincipal Jwt principal,
                                                        @Valid @RequestBody ExportRequest request) {
        // Senha conferida e dados lidos antes de a resposta começar: um erro ainda vira 401/422 normal.
        AccountDataUseCase.Export export = accountData.export(Viewer.requireId(principal), request.currentPassword());
        byte[] data = toJson(export);
        StreamingResponseBody body = out -> {
            try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
                put(zip, "LEIA-ME.txt", README.getBytes(StandardCharsets.UTF_8));
                put(zip, "dados.json", data);
                export.files().writeTo((path, content) -> put(zip, path, content));
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("weediario-" + export.username() + "-dados.zip").build().toString())
                .body(body);
    }

    /** Apaga a conta; a sessão deste aparelho cai junto (o cookie é apagado). */
    @PostMapping("/api/v1/me/delete")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt principal,
                                       @Valid @RequestBody DeleteRequest request) {
        accountData.delete(Viewer.requireId(principal), request.currentPassword(), request.username());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.expire().toString())
                .build();
    }

    private byte[] toJson(AccountDataUseCase.Export export) {
        try {
            return json.writeValueAsBytes(export.data());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void put(ZipOutputStream zip, String path, byte[] content) {
        try {
            zip.putNextEntry(new ZipEntry(path));
            zip.write(content);
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public record ExportRequest(@NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String currentPassword) {

        @Override
        public String toString() {
            return "ExportRequest[***]";
        }
    }

    /** @param username o apelido digitado, para confirmar que é para apagar mesmo */
    public record DeleteRequest(
            @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String currentPassword,
            @NotBlank @Size(max = Username.MAX_LENGTH) String username) {

        @Override
        public String toString() {
            return "DeleteRequest[***]";
        }
    }
}
