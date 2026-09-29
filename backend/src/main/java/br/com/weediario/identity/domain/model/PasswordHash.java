package br.com.weediario.identity.domain.model;

import br.com.weediario.shared.domain.Guard;

/**
 * Hash já calculado (Argon2id). O domínio nunca manipula a senha em texto puro além da fronteira do {@code PasswordHasher}.
 */
public record PasswordHash(String value) {

    public PasswordHash {
        Guard.notBlank(value, "Hash de senha");
    }

    @Override
    public String toString() {
        return "PasswordHash[***]";
    }
}
