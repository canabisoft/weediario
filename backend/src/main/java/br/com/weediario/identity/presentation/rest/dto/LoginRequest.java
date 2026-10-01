package br.com.weediario.identity.presentation.rest.dto;

import br.com.weediario.identity.domain.service.PasswordPolicy;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param login e-mail ou apelido. Aceita também o nome antigo do campo, {@code email}.
 */
public record LoginRequest(
        @JsonAlias("email") @NotBlank @Size(max = 254) String login,
        @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String password) {

    @Override
    public String toString() {
        return "LoginRequest[***]";
    }
}
