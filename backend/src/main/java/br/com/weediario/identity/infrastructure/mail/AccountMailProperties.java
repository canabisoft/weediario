package br.com.weediario.identity.infrastructure.mail;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * E-mail opcional das contas.
 *
 * @param emailKey  AES-256 (32 bytes em Base64) que cifra os endereços no banco. Gerada por scripts/setup-env.
 * @param publicUrl endereço do site, base dos links mandados por e-mail (ex.: https://weediario.com.br)
 * @param from      remetente; vazio (ou sem {@code spring.mail.host}) = envio desligado
 * @param logLinks  só para desenvolvimento: sem SMTP, escreve no log o link que iria por e-mail
 */
@Validated
@ConfigurationProperties(prefix = "weediario.identity.mail")
public record AccountMailProperties(@NotBlank String emailKey, String publicUrl, String from, boolean logLinks) {

    public AccountMailProperties {
        publicUrl = publicUrl == null || publicUrl.isBlank() ? "http://localhost:8080" : publicUrl.replaceAll("/+$", "");
        from = from == null ? "" : from.strip();
    }

    @Override
    public String toString() {
        return "AccountMailProperties[publicUrl=" + publicUrl + ", from=" + from + ", logLinks=" + logLinks + "]";
    }
}
