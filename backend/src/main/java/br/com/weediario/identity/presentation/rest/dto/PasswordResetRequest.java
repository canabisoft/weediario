package br.com.weediario.identity.presentation.rest.dto;

import br.com.weediario.identity.domain.model.Username;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Pedido de link de redefinição: só o apelido (o e-mail nunca localiza uma conta). */
public record PasswordResetRequest(@NotBlank @Size(max = Username.MAX_LENGTH) String username) {
}
