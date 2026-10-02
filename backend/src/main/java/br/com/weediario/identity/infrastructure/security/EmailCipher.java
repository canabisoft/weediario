package br.com.weediario.identity.infrastructure.security;

import br.com.weediario.identity.infrastructure.mail.AccountMailProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Cifra os e-mails das contas (AES-256-GCM, IV aleatório). O id da conta entra como dado autenticado: copiar o valor
 * cifrado de uma conta para outra faz a decifragem falhar. Um dump do banco sozinho não revela quem tem conta.
 * <p>
 * Formato: {@code [1][IV de 12 bytes][texto cifrado + tag de 16 bytes]}.
 */
@Component
public class EmailCipher {

    private static final byte VERSION = 1;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final String AAD_PREFIX = "weediario/account_emails/v1:";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public EmailCipher(AccountMailProperties properties) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(properties.emailKey().strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("weediario.identity.mail.email-key não é Base64 válido", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException("weediario.identity.mail.email-key precisa ter 32 bytes (AES-256)");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public byte[] encrypt(String email, UUID userId) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(userId));
            byte[] sealed = cipher.doFinal(email.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + IV_LENGTH + sealed.length).put(VERSION).put(iv).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar e-mail", e);
        }
    }

    public String decrypt(byte[] stored, UUID userId) {
        if (stored == null || stored.length < 1 + IV_LENGTH + TAG_BITS / 8 || stored[0] != VERSION) {
            throw new IllegalStateException("E-mail cifrado em formato desconhecido");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 1, IV_LENGTH));
            cipher.updateAAD(aad(userId));
            return new String(cipher.doFinal(stored, 1 + IV_LENGTH, stored.length - 1 - IV_LENGTH),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao decifrar e-mail (chave errada ou dado trocado de conta)", e);
        }
    }

    private static byte[] aad(UUID userId) {
        return (AAD_PREFIX + userId).getBytes(StandardCharsets.UTF_8);
    }
}
