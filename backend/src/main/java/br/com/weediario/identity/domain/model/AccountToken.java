package br.com.weediario.identity.domain.model;

import br.com.weediario.shared.domain.Guard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Token de uso único enviado por e-mail (confirmar o endereço ou redefinir a senha). 256 bits aleatórios; o banco
 * guarda só o SHA-256, então um dump não permite usar links ainda válidos.
 */
public final class AccountToken {

    public enum Purpose {
        VERIFY_EMAIL(Duration.ofHours(24)),
        RESET_PASSWORD(Duration.ofMinutes(30));

        private final Duration ttl;

        Purpose(Duration ttl) {
            this.ttl = ttl;
        }

        public Duration ttl() {
            return ttl;
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final UUID id;
    private final UserId userId;
    private final Purpose purpose;
    private final String tokenHash;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant usedAt;

    private AccountToken(UUID id, UserId userId, Purpose purpose, String tokenHash, Instant createdAt,
                         Instant expiresAt, Instant usedAt) {
        this.id = Guard.notNull(id, "Id");
        this.userId = Guard.notNull(userId, "Usuário");
        this.purpose = Guard.notNull(purpose, "Finalidade");
        this.tokenHash = Guard.notBlank(tokenHash, "Hash do token");
        this.createdAt = Guard.notNull(createdAt, "Criação");
        this.expiresAt = Guard.notNull(expiresAt, "Expiração");
        this.usedAt = usedAt;
    }

    /** O valor bruto vai só no link do e-mail; aqui fica o hash. */
    public record Issued(AccountToken token, String rawValue) {

        @Override
        public String toString() {
            return "AccountToken.Issued[" + token.purpose + "]";
        }
    }

    public static Issued issue(UserId userId, Purpose purpose, Instant now) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AccountToken token = new AccountToken(UUID.randomUUID(), userId, purpose, hashOf(raw), now,
                now.plus(purpose.ttl()), null);
        return new Issued(token, raw);
    }

    public static AccountToken restore(UUID id, UserId userId, Purpose purpose, String tokenHash, Instant createdAt,
                                       Instant expiresAt, Instant usedAt) {
        return new AccountToken(id, userId, purpose, tokenHash, createdAt, expiresAt, usedAt);
    }

    public static String hashOf(String rawValue) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }

    public boolean isUsable(Purpose expected, Instant now) {
        return purpose == expected && usedAt == null && now.isBefore(expiresAt);
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public Purpose purpose() {
        return purpose;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant usedAt() {
        return usedAt;
    }
}
