package br.com.weediario.treatment.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param encryptionKey chave AES-256 em Base64 (32 bytes) para os textos de saúde. Perder a chave torna esses
 *                      textos ilegíveis; trocá-la exige recifrar os dados.
 */
@Validated
@ConfigurationProperties(prefix = "weediario.treatment")
public record TreatmentProperties(@NotBlank String encryptionKey) {

    @Override
    public String toString() {
        return "TreatmentProperties[***]";
    }
}
