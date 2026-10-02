package br.com.weediario.identity.presentation.rest;

import br.com.weediario.identity.application.AuthenticatedSession;
import br.com.weediario.identity.presentation.rest.dto.AccessTokenResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/** Resposta que abre uma sessão: access token no corpo, refresh token no cookie HttpOnly, nada em cache. */
@Component
public class SessionResponses {

    private final RefreshTokenCookieFactory cookies;

    public SessionResponses(RefreshTokenCookieFactory cookies) {
        this.cookies = cookies;
    }

    public ResponseEntity<AccessTokenResponse> open(HttpStatus status, AuthenticatedSession session) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE,
                        cookies.create(session.refreshToken(), session.refreshTokenExpiresAt()).toString())
                .body(AccessTokenResponse.bearer(session.accessToken(), session.accessTokenExpiresAt()));
    }

    public ResponseEntity<AccessTokenResponse> open(AuthenticatedSession session) {
        return open(HttpStatus.OK, session);
    }
}
