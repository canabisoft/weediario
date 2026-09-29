package br.com.weediario.social.infrastructure.security;

import br.com.weediario.social.application.port.Pseudonymizer;
import br.com.weediario.social.domain.model.ReactorPseudonym;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * HMAC-SHA256(chave, usuário + alvo). Sem a chave, que fica fora do banco, não dá para testar
 * "o usuário X reagiu a este diário?" nem correlacionar reações de uma mesma pessoa entre diários.
 */
@Component
public class HmacPseudonymizer implements Pseudonymizer {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKeySpec key;

    public HmacPseudonymizer(SocialProperties properties) {
        byte[] keyBytes = Base64.getDecoder().decode(properties.pseudonymKey());
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("weediario.social.pseudonym-key deve ter no mínimo 256 bits");
        }
        this.key = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    @Override
    public ReactorPseudonym pseudonymOf(UUID userId, UUID target) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal((userId + ":" + target).getBytes(StandardCharsets.UTF_8));
            return new ReactorPseudonym(HexFormat.of().formatHex(digest));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC indisponível na JVM", e);
        }
    }
}
