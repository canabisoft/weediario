package br.com.weediario.treatment.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Chaves AES-256 (Base64, 32 bytes) dos textos de saúde. Perder a chave torna esses textos ilegíveis.
 *
 * @param encryptionKey     chave atual, direto na configuração (variável {@code WEEDIARIO_HEALTH_KEY})
 * @param encryptionKeyFile alternativa mais segura: arquivo com a chave (ex. docker secret em
 *                          {@code /run/secrets/...}), que não aparece em {@code docker inspect} nem no ambiente
 * @param previousKeys      chaves antigas, separadas por vírgula: só decifram, durante a troca de chave
 */
@Validated
@ConfigurationProperties(prefix = "weediario.treatment")
public record TreatmentProperties(String encryptionKey, String encryptionKeyFile, String previousKeys) {

    /** A chave atual: do arquivo, se configurado; senão, da propriedade. */
    public String currentKey() {
        if (encryptionKeyFile != null && !encryptionKeyFile.isBlank()) {
            try {
                return Files.readString(Path.of(encryptionKeyFile.strip()), StandardCharsets.UTF_8).strip();
            } catch (IOException e) {
                throw new UncheckedIOException("Não foi possível ler weediario.treatment.encryption-key-file", e);
            }
        }
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException(
                    "Configure weediario.treatment.encryption-key ou weediario.treatment.encryption-key-file");
        }
        return encryptionKey;
    }

    public List<String> previousKeyList() {
        return previousKeys == null || previousKeys.isBlank() ? List.of()
                : Arrays.stream(previousKeys.split(",")).map(String::strip).filter(k -> !k.isEmpty()).toList();
    }

    @Override
    public String toString() {
        return "TreatmentProperties[***]";
    }
}
