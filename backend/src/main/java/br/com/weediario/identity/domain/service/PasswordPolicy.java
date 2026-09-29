package br.com.weediario.identity.domain.service;

import br.com.weediario.shared.domain.DomainException;

/**
 * Política de senha alinhada ao NIST SP 800-63B: comprimento mínimo alto, sem regras de composição arbitrárias.
 * O máximo evita DoS via hash de entradas gigantes.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    public static void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new DomainException("Senha é obrigatória");
        }
        int length = rawPassword.codePointCount(0, rawPassword.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new DomainException("A senha deve ter entre " + MIN_LENGTH + " e " + MAX_LENGTH + " caracteres");
        }
    }
}
