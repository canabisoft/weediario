package br.com.weediario.identity.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * E-mail de login. Nunca é exposto publicamente nem incluído em tokens.
 */
public record Email(String value) {

    private static final int MAX_LENGTH = 254;
    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        value = Guard.notBlank(value, "E-mail").toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new DomainException("E-mail inválido");
        }
    }

    /** Evita que o e-mail vaze em logs por acidente. */
    @Override
    public String toString() {
        return "Email[***]";
    }
}
