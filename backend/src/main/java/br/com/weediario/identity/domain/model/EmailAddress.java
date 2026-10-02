package br.com.weediario.identity.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * E-mail opcional da conta, usado só para recuperar a senha e para avisos de segurança. Nunca é público, nunca é
 * único (o cadastro não revela se um e-mail já tem conta) e nunca serve para login ou busca: é guardado cifrado.
 */
public record EmailAddress(String value) {

    public static final int MAX_LENGTH = 254;
    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s.]{2,}$");

    public EmailAddress {
        value = Guard.notBlank(value, "E-mail").strip().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new DomainException("E-mail inválido");
        }
    }

    /** Versão para mostrar ao dono sem expor o endereço inteiro na tela: {@code br***@gm***.com}. */
    public String masked() {
        int at = value.indexOf('@');
        int dot = value.lastIndexOf('.');
        String local = value.substring(0, at);
        String domain = value.substring(at + 1, dot);
        return keep(local, 2) + "***@" + keep(domain, 2) + "***" + value.substring(dot);
    }

    private static String keep(String part, int count) {
        return part.substring(0, Math.min(count, part.length()));
    }

    @Override
    public String toString() {
        return "EmailAddress[" + masked() + "]";
    }
}
