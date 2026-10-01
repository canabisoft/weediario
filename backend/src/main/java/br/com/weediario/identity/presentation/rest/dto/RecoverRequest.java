package br.com.weediario.identity.presentation.rest.dto;

import br.com.weediario.identity.domain.model.RecoveryVerifier;
import br.com.weediario.identity.domain.model.Username;
import br.com.weediario.identity.domain.service.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Recuperação de conta: apelido, derivado da frase de 12 palavras (calculado no navegador) e a senha nova.
 */
public record RecoverRequest(
        @NotBlank @Size(max = Username.MAX_LENGTH) String username,
        @NotBlank @Size(max = RecoveryVerifier.LENGTH) String recoveryVerifier,
        @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH) String newPassword) {

    /** Nada daqui vai para log. */
    @Override
    public String toString() {
        return "RecoverRequest[***]";
    }
}
