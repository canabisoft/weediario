package br.com.weediario.identity.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * O que o banco guarda da chave de acesso: o SHA-256 dela em hexadecimal (único por conta). A chave em si não é
 * guardada em lugar nenhum.
 */
public record AccessKeyHash(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[0-9a-f]{64}$");

    public AccessKeyHash {
        Guard.notBlank(value, "Hash da chave de acesso");
        if (!FORMAT.matcher(value).matches()) {
            throw new DomainException("Hash da chave de acesso inválido");
        }
    }

    /** Comparação em tempo constante. */
    public boolean matches(AccessKeyHash other) {
        return other != null && MessageDigest.isEqual(value.getBytes(StandardCharsets.US_ASCII),
                other.value.getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public String toString() {
        return "AccessKeyHash[***]";
    }
}
