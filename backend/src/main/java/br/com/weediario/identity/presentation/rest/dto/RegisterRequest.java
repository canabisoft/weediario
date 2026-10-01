package br.com.weediario.identity.presentation.rest.dto;

import br.com.weediario.identity.domain.model.Username;
import br.com.weediario.identity.domain.service.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Size(min = Username.MIN_LENGTH, max = Username.MAX_LENGTH)
        @Pattern(regexp = "^[A-Za-z0-9_.]+$", message = "use apenas letras, números, '_' e '.'")
        String username,

        @NotBlank @Email @Size(max = 254)
        String email,

        @NotBlank
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
        String password,

        /** Versão dos Termos de Uso aceita (caixa marcada no cadastro). Sem ela, não há cadastro. */
        @NotBlank @Size(max = 10)
        String acceptedTermsVersion) {

    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + "]";
    }
}
