package br.com.weediario.shared.domain;

import java.util.Objects;

/**
 * Pré-condições reutilizáveis pelos value objects e entidades do domínio.
 */
public final class Guard {

    private Guard() {
    }

    public static <T> T notNull(T value, String field) {
        if (value == null) {
            throw new DomainException(field + " é obrigatório");
        }
        return value;
    }

    public static String notBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + " é obrigatório");
        }
        return value.strip();
    }

    public static String lengthBetween(String value, int min, int max, String field) {
        Objects.requireNonNull(value);
        if (value.length() < min || value.length() > max) {
            throw new DomainException(field + " deve ter entre " + min + " e " + max + " caracteres");
        }
        return value;
    }
}
