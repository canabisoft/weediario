package br.com.weediario.identity.presentation.rest;

import br.com.weediario.identity.application.LoginThrottle;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * "Este aparelho já entrou nesta conta" (OWASP, device cookies): depois de um login, cadastro ou recuperação, o
 * navegador guarda um identificador aleatório assinado junto com o apelido. No próximo login, quem traz esse cookie tem
 * uma contagem de tentativas só sua ({@link LoginThrottle#beginOnDevice}), então errar a senha de propósito no apelido
 * de alguém não tranca o dono para fora.
 * <p>
 * Não identifica a pessoa nem o aparelho para nada além disso: não é guardado no servidor, só vai para o endpoint de
 * login (path restrito), é HttpOnly, Secure e SameSite=Strict, e serve para um apelido só.
 */
@Component
public class TrustedDeviceCookie {

    static final String NAME = "__Secure-wd_dev";
    static final String PATH = "/api/v1/auth/login";
    static final Duration MAX_AGE = Duration.ofDays(180);

    private static final String HMAC = "HmacSHA256";
    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_-]{22}\\.[A-Za-z0-9_-]{43}$");
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TrustedDeviceCookie(SecretKey jwtSigningKey) {
        this.key = new SecretKeySpec(hmac(new SecretKeySpec(jwtSigningKey.getEncoded(), HMAC),
                "weediario/trusted-device/v1"), HMAC);
    }

    /** Cookie novo para quem acabou de provar que é dono do apelido. */
    public ResponseCookie issue(String rawUsername) {
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        String device = ENCODER.encodeToString(nonce);
        return ResponseCookie.from(NAME, device + "." + sign(device, rawUsername))
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(MAX_AGE)
                .build();
    }

    /** O aparelho, se o cookie veio, está íntegro e foi emitido para este mesmo apelido. */
    public Optional<String> deviceFor(HttpServletRequest request, String rawUsername) {
        var cookie = WebUtils.getCookie(request, NAME);
        if (cookie == null || cookie.getValue() == null || !FORMAT.matcher(cookie.getValue()).matches()) {
            return Optional.empty();
        }
        String[] parts = cookie.getValue().split("\\.");
        boolean valid = MessageDigest.isEqual(sign(parts[0], rawUsername).getBytes(StandardCharsets.US_ASCII),
                parts[1].getBytes(StandardCharsets.US_ASCII));
        return valid ? Optional.of(parts[0]) : Optional.empty();
    }

    private String sign(String device, String rawUsername) {
        return ENCODER.encodeToString(hmac(key, device + ":" + LoginThrottle.normalize(rawUsername)));
    }

    private static byte[] hmac(SecretKeySpec key, String message) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC indisponível na JVM", e);
        }
    }
}
