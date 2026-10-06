package br.com.weediario.identity.presentation.rest.dto;

import br.com.weediario.identity.domain.model.EmailAddress;
import br.com.weediario.identity.domain.model.RecoveryVerifier;
import br.com.weediario.identity.domain.model.Username;
import br.com.weediario.identity.domain.service.PasswordPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cadastro: apelido, senha, e-mail opcional e o derivado da frase de recuperação. Nenhum dado civil.
 */
public record RegisterRequest(
        @NotBlank
        @Size(min = Username.MIN_LENGTH, max = Username.MAX_LENGTH)
        @Pattern(regexp = "^[A-Za-z0-9_.]+$", message = "use apenas letras, números, '_' e '.'")
        String username,
        @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH)
        String password,
        /** Opcional: só para recuperar a senha e receber avisos de segurança. */
        @Size(max = EmailAddress.MAX_LENGTH)
        String email,
        /**
         * Derivado (PBKDF2) da frase de 12 palavras gerada no navegador. As palavras nunca são enviadas; sem frase,
         * não há cadastro, porque ela é o jeito de recuperar que toda conta tem (o e-mail é opcional).
         */
        @NotBlank @Size(max = RecoveryVerifier.LENGTH)
        String recoveryVerifier,
        /** Versão dos Termos de Uso aceita (caixa marcada no cadastro). Sem ela, não há cadastro. */
        @NotBlank @Size(max = 10)
        String acceptedTermsVersion,
        /** Solução do desafio anti-robô de {@code GET /api/v1/auth/register-challenge}. */
        @Valid
        Challenge challenge) {

    /** Tamanhos fixos do protocolo: hex de 64 caracteres e o salt com o prazo. */
    public record Challenge(@Size(max = 64) String challenge, @Size(max = 64) String salt, long number,
                            @Size(max = 64) String signature) {
    }

    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + "]";
    }
}
