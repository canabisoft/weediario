package br.com.weediario.identity.presentation.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailVerifyRequest(@NotBlank @Size(max = 128) String token) {

    @Override
    public String toString() {
        return "EmailVerifyRequest[***]";
    }
}
