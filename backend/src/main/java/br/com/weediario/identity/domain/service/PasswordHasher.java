package br.com.weediario.identity.domain.service;

import br.com.weediario.identity.domain.model.PasswordHash;

/**
 * Porta de domínio para o algoritmo de hash de senha (implementada com Argon2id na infraestrutura).
 */
public interface PasswordHasher {

    PasswordHash hash(String rawPassword);

    boolean matches(String rawPassword, PasswordHash hash);
}
