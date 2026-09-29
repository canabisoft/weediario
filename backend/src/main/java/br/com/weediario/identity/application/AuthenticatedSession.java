package br.com.weediario.identity.application;

import java.time.Instant;

/**
 * Resultado de login/refresh. O access token vai no corpo da resposta (guardado só em memória pelo Angular);
 * o refresh token vai exclusivamente no cookie HttpOnly.
 */
public record AuthenticatedSession(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt) {

    @Override
    public String toString() {
        return "AuthenticatedSession[***]";
    }
}
