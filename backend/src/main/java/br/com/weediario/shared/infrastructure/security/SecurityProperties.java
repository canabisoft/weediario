package br.com.weediario.shared.infrastructure.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "weediario.security")
public record SecurityProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull RefreshToken refreshToken,
        @Valid @NotNull Cors cors) {

    /**
     * @param secret chave HMAC em Base64 (mínimo 256 bits), vinda de variável de ambiente, nunca do repositório.
     */
    public record Jwt(@NotBlank String issuer, @NotBlank String secret, @NotNull Duration accessTokenTtl) {

        @Override
        public String toString() {
            return "Jwt[issuer=" + issuer + ", accessTokenTtl=" + accessTokenTtl + "]";
        }
    }

    public record RefreshToken(@NotNull Duration ttl, @NotBlank String cookieName, @NotBlank String cookiePath) {
    }

    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }
}
