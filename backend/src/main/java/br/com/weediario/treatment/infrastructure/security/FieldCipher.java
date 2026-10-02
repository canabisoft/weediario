package br.com.weediario.treatment.infrastructure.security;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cifra os textos livres de saúde (nomes de paciente e de sintomas, observações) antes de irem ao banco, com
 * AES-256-GCM e IV aleatório por valor. Um dump do banco sozinho não revela esses textos.
 * <p>
 * Formato atual (v2): {@code [2][id da chave, 4 bytes][IV de 12 bytes][texto cifrado + tag de 16 bytes]}.
 * <ul>
 *   <li>O <b>contexto</b> ({@code tabela.coluna:id da linha}) entra como dado autenticado (AAD): copiar o valor
 *   cifrado de um paciente para outro, ou de uma coluna para outra, faz a decifragem falhar.</li>
 *   <li>O <b>id da chave</b> (início do SHA-256 dela) permite trocar a chave sem parar: a atual cifra, as antigas
 *   ({@code previous-keys}) ainda decifram, e {@link TreatmentRecipher} regrava tudo com a atual.</li>
 * </ul>
 * O formato v1 (AAD fixo, uma chave só) ainda é lido, para os dados gravados antes; o recifrador converte.
 */
@Component
public class FieldCipher {

    static final byte V1 = 1;
    static final byte V2 = 2;
    private static final int KEY_ID_LENGTH = 4;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final byte[] V1_AAD = "weediario/treatment/v1".getBytes(StandardCharsets.UTF_8);
    private static final String V2_AAD_PREFIX = "weediario/treatment/v2/";

    private final SecretKeySpec current;
    private final byte[] currentId;
    /** Todas as chaves que decifram (atual primeiro), pelo id. */
    private final Map<String, SecretKeySpec> keys = new LinkedHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public FieldCipher(TreatmentProperties properties) {
        this.current = keyOf(properties.currentKey(), "weediario.treatment.encryption-key");
        this.currentId = idOf(current);
        keys.put(Arrays.toString(currentId), current);
        for (String previous : properties.previousKeyList()) {
            SecretKeySpec key = keyOf(previous, "weediario.treatment.previous-keys");
            keys.putIfAbsent(Arrays.toString(idOf(key)), key);
        }
    }

    /**
     * @param context onde o valor mora, ex. {@code treatment_patients.name_enc:<id>} (ver {@link #context})
     */
    public byte[] encrypt(String plain, String context) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, current, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(context));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + KEY_ID_LENGTH + IV_LENGTH + sealed.length)
                    .put(V2).put(currentId).put(iv).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar", e);
        }
    }

    public String decrypt(byte[] stored, String context) {
        if (stored == null) {
            return null;
        }
        try {
            if (stored.length > 0 && stored[0] == V2 && stored.length >= 1 + KEY_ID_LENGTH + IV_LENGTH + TAG_BITS / 8) {
                byte[] id = Arrays.copyOfRange(stored, 1, 1 + KEY_ID_LENGTH);
                SecretKeySpec key = keys.get(Arrays.toString(id));
                if (key == null) {
                    throw new IllegalStateException("Valor cifrado com uma chave que não está configurada");
                }
                int offset = 1 + KEY_ID_LENGTH;
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, offset, IV_LENGTH));
                cipher.updateAAD(aad(context));
                return new String(cipher.doFinal(stored, offset + IV_LENGTH, stored.length - offset - IV_LENGTH),
                        StandardCharsets.UTF_8);
            }
            if (stored.length > 0 && stored[0] == V1 && stored.length >= 1 + IV_LENGTH + TAG_BITS / 8) {
                return decryptV1(stored);
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao decifrar (chave errada ou dado adulterado/trocado de lugar)", e);
        }
        throw new IllegalStateException("Valor cifrado em formato desconhecido");
    }

    /** Se o valor precisa ser regravado (formato antigo ou chave antiga). */
    public boolean needsRecipher(byte[] stored) {
        if (stored == null) {
            return false;
        }
        return stored[0] != V2 || !Arrays.equals(Arrays.copyOfRange(stored, 1, 1 + KEY_ID_LENGTH), currentId);
    }

    /** Contexto padrão de um valor cifrado: tabela, coluna e id da linha. */
    public static String context(String table, String column, Object rowId) {
        return table + "." + column + ":" + rowId;
    }

    /** v1: AAD fixo; tenta cada chave configurada (a v1 não guardava qual foi usada). */
    private String decryptV1(byte[] stored) throws GeneralSecurityException {
        GeneralSecurityException last = null;
        for (SecretKeySpec key : List.copyOf(keys.values())) {
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 1, IV_LENGTH));
                cipher.updateAAD(V1_AAD);
                return new String(cipher.doFinal(stored, 1 + IV_LENGTH, stored.length - 1 - IV_LENGTH),
                        StandardCharsets.UTF_8);
            } catch (GeneralSecurityException e) {
                last = e;
            }
        }
        throw last;
    }

    private static byte[] aad(String context) {
        if (context == null || context.isBlank()) {
            throw new IllegalArgumentException("Contexto obrigatório para cifrar dado de saúde");
        }
        return (V2_AAD_PREFIX + context).getBytes(StandardCharsets.UTF_8);
    }

    private static SecretKeySpec keyOf(String base64, String property) {
        byte[] raw;
        try {
            raw = java.util.Base64.getDecoder().decode(base64.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(property + " precisa estar em Base64", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException(property + " precisa ter 32 bytes (AES-256) em Base64");
        }
        return new SecretKeySpec(raw, "AES");
    }

    private static byte[] idOf(SecretKeySpec key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getEncoded());
            return Arrays.copyOf(digest, KEY_ID_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
