package br.com.weediario.identity.presentation.rest.dto;

import br.com.weediario.identity.domain.service.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank @Size(max = 128) String token,
        @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String newPassword) {

    @Override
    public String toString() {
        return "PasswordResetConfirmRequest[***]";
    }
}
