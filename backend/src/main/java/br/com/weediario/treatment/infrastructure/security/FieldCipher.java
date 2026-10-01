package br.com.weediario.treatment.infrastructure.security;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra os textos livres de saúde (nomes de paciente e de sintomas, observações) antes de irem ao banco, com
 * AES-256-GCM e IV aleatório por valor. Um dump do banco sozinho não revela esses textos.
 * <p>
 * Formato: {@code [versão=1][IV de 12 bytes][texto cifrado + tag de 16 bytes]}.
 */
@Component
public class FieldCipher {

    private static final byte VERSION = 1;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final byte[] AAD = "weediario/treatment/v1".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public FieldCipher(TreatmentProperties properties) {
        byte[] raw = Base64.getDecoder().decode(properties.encryptionKey());
        if (raw.length != 32) {
            throw new IllegalStateException("weediario.treatment.encryption-key precisa ter 32 bytes (AES-256) em Base64");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public byte[] encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(AAD);
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + IV_LENGTH + sealed.length).put(VERSION).put(iv).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar", e);
        }
    }

    public String decrypt(byte[] stored) {
        if (stored == null) {
            return null;
        }
        if (stored.length < 1 + IV_LENGTH + TAG_BITS / 8 || stored[0] != VERSION) {
            throw new IllegalStateException("Valor cifrado em formato desconhecido");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 1, IV_LENGTH));
            cipher.updateAAD(AAD);
            byte[] plain = cipher.doFinal(stored, 1 + IV_LENGTH, stored.length - 1 - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao decifrar (chave errada ou dado adulterado)", e);
        }
    }
}
