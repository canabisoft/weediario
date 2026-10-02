package br.com.weediario.treatment.infrastructure.security;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldCipherTest {

    private static final String KEY_A = Base64.getEncoder().encodeToString("a".repeat(32).getBytes(StandardCharsets.US_ASCII));
    private static final String KEY_B = Base64.getEncoder().encodeToString("b".repeat(32).getBytes(StandardCharsets.US_ASCII));

    private static FieldCipher cipher(String current, String previous) {
        return new FieldCipher(new TreatmentProperties(current, null, previous));
    }

    @Test
    void valueIsBoundToItsRowAndColumn() {
        FieldCipher cipher = cipher(KEY_A, null);
        String patientA = FieldCipher.context("treatment_patients", "name_enc", UUID.randomUUID());
        String patientB = FieldCipher.context("treatment_patients", "name_enc", UUID.randomUUID());

        byte[] sealed = cipher.encrypt("Maria", patientA);
        assertThat(cipher.decrypt(sealed, patientA)).isEqualTo("Maria");
        // Copiar o valor cifrado para outra linha (ou outra coluna) faz a decifragem falhar.
        assertThatThrownBy(() -> cipher.decrypt(sealed, patientB)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(sealed, patientA.replace("name_enc", "notes_enc")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rotatesKeysWithoutDowntime() {
        String context = FieldCipher.context("treatment_doses", "notes_enc", UUID.randomUUID());
        byte[] withOldKey = cipher(KEY_A, null).encrypt("dormiu melhor", context);

        FieldCipher rotated = cipher(KEY_B, KEY_A);
        assertThat(rotated.decrypt(withOldKey, context)).isEqualTo("dormiu melhor");
        assertThat(rotated.needsRecipher(withOldKey)).isTrue();
        byte[] resealed = rotated.encrypt(rotated.decrypt(withOldKey, context), context);
        assertThat(rotated.needsRecipher(resealed)).isFalse();

        // Sem a chave antiga configurada, o valor antigo não abre (falha fechada).
        assertThatThrownBy(() -> cipher(KEY_B, null).decrypt(withOldKey, context)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void stillReadsTheOldFormatAndMarksItForRecipher() throws Exception {
        byte[] v1 = legacyV1(KEY_A, "Insônia");
        FieldCipher cipher = cipher(KEY_A, null);
        assertThat(cipher.decrypt(v1, "qualquer:contexto")).isEqualTo("Insônia");
        assertThat(cipher.needsRecipher(v1)).isTrue();
    }

    @Test
    void readsTheKeyFromAFileLikeADockerSecret() throws Exception {
        Path file = Files.createTempFile("health-key", ".txt");
        try {
            Files.writeString(file, KEY_A + "\n");
            FieldCipher fromFile = new FieldCipher(new TreatmentProperties(null, file.toString(), null));
            String context = FieldCipher.context("treatment_symptoms", "name_enc", UUID.randomUUID());
            assertThat(cipher(KEY_A, null).decrypt(fromFile.encrypt("dor", context), context)).isEqualTo("dor");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** Formato antigo: [1][IV 12][cifrado+tag], AAD fixo. */
    private static byte[] legacyV1(String base64Key, String plain) throws Exception {
        byte[] iv = new byte[12];
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES"),
                new GCMParameterSpec(128, iv));
        c.updateAAD("weediario/treatment/v1".getBytes(StandardCharsets.UTF_8));
        byte[] sealed = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        return ByteBuffer.allocate(1 + 12 + sealed.length).put((byte) 1).put(iv).put(sealed).array();
    }
}
