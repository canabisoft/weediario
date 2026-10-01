package br.com.weediario.identity.presentation.rest.dto;

import br.com.weediario.identity.domain.model.AccessKey;
import br.com.weediario.identity.domain.model.Username;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login com apelido + chave de acesso ({@code wd_sec_...}). Não existe senha nem e-mail.
 *
 * @param login apelido. Aceita também o nome {@code username}.
 */
public record LoginRequest(
        @JsonAlias("username") @NotBlank @Size(max = Username.MAX_LENGTH) String login,
        @NotBlank @Size(max = AccessKey.LENGTH) String accessKey) {

    @Override
    public String toString() {
        return "LoginRequest[***]";
    }
}
