package br.com.weediario.social.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param pseudonymKey chave HMAC em Base64 (mínimo 256 bits). Trocá-la "zera" quem reagiu a quê
 *                     (as reações antigas continuam contando, mas não podem mais ser desfeitas).
 */
@Validated
@ConfigurationProperties(prefix = "weediario.social")
public record SocialProperties(@NotBlank String pseudonymKey) {

    @Override
    public String toString() {
        return "SocialProperties[***]";
    }
}
