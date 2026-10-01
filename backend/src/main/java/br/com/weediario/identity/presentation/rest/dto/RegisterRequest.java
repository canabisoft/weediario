package br.com.weediario.identity.presentation.rest.dto;

import br.com.weediario.identity.domain.model.RecoveryVerifier;
import br.com.weediario.identity.domain.model.Username;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cadastro sem e-mail, senha ou dado civil: a chave de acesso é gerada pelo servidor e volta na resposta.
 */
public record RegisterRequest(
        @NotBlank
        @Size(min = Username.MIN_LENGTH, max = Username.MAX_LENGTH)
        @Pattern(regexp = "^[A-Za-z0-9_.]+$", message = "use apenas letras, números, '_' e '.'")
        String username,

        /**
         * Derivado (PBKDF2) da frase de 12 palavras gerada no navegador. As palavras nunca são enviadas; sem frase,
         * não há cadastro, porque ela é o único jeito de recuperar a conta.
         */
        @NotBlank @Size(max = RecoveryVerifier.LENGTH)
        String recoveryVerifier,

        /** Versão dos Termos de Uso aceita (caixa marcada no cadastro). Sem ela, não há cadastro. */
        @NotBlank @Size(max = 10)
        String acceptedTermsVersion) {

    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + "]";
    }
}
