package br.com.weediario.identity.domain.service;

import br.com.weediario.identity.domain.model.PasswordHash;

/**
 * Porta de domínio para hash lento (Argon2id na infraestrutura): guarda e confere a senha e o derivado da frase de
 * recuperação.
 */
public interface PasswordHasher {

    PasswordHash hash(String rawPassword);

    boolean matches(String rawPassword, PasswordHash hash);
}
