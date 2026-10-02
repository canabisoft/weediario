package br.com.weediario.identity.presentation.rest.dto;

import br.com.weediario.identity.domain.model.Username;
import br.com.weediario.identity.domain.service.PasswordPolicy;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login com apelido + senha.
 *
 * @param login apelido. Aceita também o nome {@code username}.
 */
public record LoginRequest(
        @JsonAlias("username") @NotBlank @Size(max = Username.MAX_LENGTH) String login,
        @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String password) {

    @Override
    public String toString() {
        return "LoginRequest[***]";
    }
}
