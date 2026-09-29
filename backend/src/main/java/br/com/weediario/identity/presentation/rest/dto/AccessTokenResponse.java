package br.com.weediario.identity.presentation.rest.dto;

import java.time.Instant;

/**
 * O Angular deve manter o access token apenas em memória (nunca localStorage/sessionStorage).
 */
public record AccessTokenResponse(String accessToken, String tokenType, Instant expiresAt) {

    public static AccessTokenResponse bearer(String accessToken, Instant expiresAt) {
        return new AccessTokenResponse(accessToken, "Bearer", expiresAt);
    }
}
