package br.com.weediario.identity.presentation.rest;

import br.com.weediario.shared.infrastructure.security.SecurityProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Cookie do refresh token: HttpOnly (inacessível a JS/XSS), Secure (só HTTPS), SameSite=Strict (sem envio cross-site)
 * e path restrito aos endpoints de autenticação, para que não trafegue nas demais requisições.
 */
@Component
public class RefreshTokenCookieFactory {

    private final SecurityProperties.RefreshToken properties;
    private final Clock clock;

    public RefreshTokenCookieFactory(SecurityProperties properties, Clock clock) {
        this.properties = properties.refreshToken();
        this.clock = clock;
    }

    public String cookieName() {
        return properties.cookieName();
    }

    public ResponseCookie create(String rawToken, Instant expiresAt) {
        return base(rawToken).maxAge(Duration.between(clock.instant(), expiresAt)).build();
    }

    public ResponseCookie expire() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(properties.cookieName(), value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(properties.cookiePath());
    }
}
