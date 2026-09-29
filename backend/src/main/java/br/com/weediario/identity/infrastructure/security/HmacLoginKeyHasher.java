package br.com.weediario.identity.infrastructure.security;

import br.com.weediario.identity.application.port.LoginKeyHasher;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * HMAC-SHA256 do e-mail com uma subchave derivada da chave do JWT (derivação por rótulo, para não usar a
 * mesma chave em duas finalidades). Sem a chave, a tabela de tentativas não revela nenhum e-mail.
 */
@Component
public class HmacLoginKeyHasher implements LoginKeyHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public HmacLoginKeyHasher(SecretKey jwtSigningKey) {
        this.key = new SecretKeySpec(hmac(new SecretKeySpec(jwtSigningKey.getEncoded(), ALGORITHM),
                "weediario/login-throttle/v1"), ALGORITHM);
    }

    @Override
    public String keyOf(String normalizedEmail) {
        return HexFormat.of().formatHex(hmac(key, normalizedEmail));
    }

    private static byte[] hmac(SecretKeySpec key, String message) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC indisponível na JVM", e);
        }
    }
}
