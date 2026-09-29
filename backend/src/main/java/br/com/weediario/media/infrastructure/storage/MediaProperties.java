package br.com.weediario.media.infrastructure.storage;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "weediario.media")
public record MediaProperties(@NotBlank String storagePath) {
}
