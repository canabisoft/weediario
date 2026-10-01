package br.com.weediario.identity.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Chave de acesso: a credencial de login no lugar da senha (apelido + chave). 256 bits do {@link SecureRandom}, em
 * base64url, com o prefixo {@value #PREFIX} (fácil de reconhecer num gerenciador de senhas ou num vazamento).
 * <p>
 * O servidor mostra a chave uma única vez (no cadastro ou ao recuperar a conta) e guarda só o {@link AccessKeyHash}.
 */
public record AccessKey(String value) {

    public static final String PREFIX = "wd_sec_";
    private static final int RANDOM_BYTES = 32;
    /** Prefixo (7) + 32 bytes em base64url sem preenchimento (43). Constante para caber em anotações. */
    public static final int LENGTH = 50;
    private static final Pattern FORMAT = Pattern.compile("^" + PREFIX + "[A-Za-z0-9_-]{43}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    public AccessKey {
        value = Guard.notBlank(value, "Chave de acesso").strip();
        if (!isWellFormed(value)) {
            throw new DomainException("Chave de acesso inválida");
        }
    }

    public static AccessKey generate() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return new AccessKey(PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    public static boolean isWellFormed(String raw) {
        return raw != null && FORMAT.matcher(raw.strip()).matches();
    }

    /**
     * SHA-256 da chave. Um hash rápido basta (e permite o índice único): com 256 bits aleatórios, não existe ataque
     * de dicionário ou força bruta contra o hash, ao contrário de uma senha escolhida por gente.
     */
    public AccessKeyHash hash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
            return new AccessKeyHash(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }

    /** Nunca vai para log por acidente. */
    @Override
    public String toString() {
        return "AccessKey[***]";
    }
}
