package br.com.weediario.identity.domain.service;

import br.com.weediario.identity.domain.model.PasswordHash;

/**
 * Porta de domínio para hash lento (Argon2id na infraestrutura): guarda o derivado da frase de recuperação e confere
 * a senha de contas anteriores à chave de acesso. A chave de acesso usa SHA-256 (ver {@code AccessKey#hash}).
 */
public interface PasswordHasher {

    PasswordHash hash(String rawPassword);

    boolean matches(String rawPassword, PasswordHash hash);
}
