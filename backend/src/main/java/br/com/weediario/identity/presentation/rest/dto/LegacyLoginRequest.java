package br.com.weediario.identity.presentation.rest.dto;

import br.com.weediario.identity.domain.model.Username;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Último login com senha de uma conta anterior à chave de acesso. A resposta traz a chave nova e a senha é apagada.
 */
public record LegacyLoginRequest(
        @NotBlank @Size(max = Username.MAX_LENGTH) String login,
        @NotBlank @Size(max = 128) String password) {

    @Override
    public String toString() {
        return "LegacyLoginRequest[***]";
    }
}
