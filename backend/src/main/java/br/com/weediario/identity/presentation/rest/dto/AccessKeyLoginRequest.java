package br.com.weediario.identity.presentation.rest.dto;

import br.com.weediario.identity.domain.model.AccessKey;
import br.com.weediario.identity.domain.model.Username;
import br.com.weediario.identity.domain.service.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Conta da época da chave de acesso: último login com a chave, criando a senha. */
public record AccessKeyLoginRequest(
        @NotBlank @Size(max = Username.MAX_LENGTH) String login,
        @NotBlank @Size(max = AccessKey.LENGTH) String accessKey,
        @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String newPassword) {

    @Override
    public String toString() {
        return "AccessKeyLoginRequest[***]";
    }
}
