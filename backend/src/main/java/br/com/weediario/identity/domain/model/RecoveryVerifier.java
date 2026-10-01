package br.com.weediario.identity.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Derivado da frase de recuperação, calculado no navegador: PBKDF2-HMAC-SHA512 (2048 iterações, como a seed do
 * BIP-39, com a passphrase {@value #PASSPHRASE}), em hexadecimal. As 12 palavras nunca chegam ao servidor; ele só
 * recebe este derivado e guarda o hash Argon2 dele.
 */
public record RecoveryVerifier(String value) {

    /** Passphrase BIP-39 usada na derivação: separa o derivado do weediário de qualquer carteira com a mesma frase. */
    public static final String PASSPHRASE = "weediario-recovery";
    public static final int LENGTH = 128;
    private static final Pattern FORMAT = Pattern.compile("^[0-9a-f]{" + LENGTH + "}$");

    public RecoveryVerifier {
        value = Guard.notBlank(value, "Frase de recuperação").strip().toLowerCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw new DomainException("Frase de recuperação inválida");
        }
    }

    /** Nunca vai para log por acidente. */
    @Override
    public String toString() {
        return "RecoveryVerifier[***]";
    }
}
