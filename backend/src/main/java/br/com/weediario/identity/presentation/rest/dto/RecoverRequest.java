package br.com.weediario.identity.presentation.rest.dto;

import br.com.weediario.identity.domain.model.RecoveryVerifier;
import br.com.weediario.identity.domain.model.Username;
import br.com.weediario.identity.domain.service.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Recuperação de conta pela frase: apelido, derivado da frase atual, senha nova e derivado de uma frase nova (a antiga
 * deixa de valer depois de digitada). Os derivados são calculados no navegador.
 */
public record RecoverRequest(
        @NotBlank @Size(max = Username.MAX_LENGTH) String username,
        @NotBlank @Size(max = RecoveryVerifier.LENGTH) String recoveryVerifier,
        @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String newPassword,
        @NotBlank @Size(max = RecoveryVerifier.LENGTH) String newRecoveryVerifier) {

    /** Nada daqui vai para log. */
    @Override
    public String toString() {
        return "RecoverRequest[***]";
    }
}
