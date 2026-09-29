package br.com.weediario.identity.application;

import br.com.weediario.identity.application.port.AccessTokenIssuer;
import br.com.weediario.identity.application.port.RefreshTokenCodec;
import br.com.weediario.identity.domain.model.RefreshToken;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.repository.RefreshTokenRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Emite o par access/refresh token, seja iniciando uma sessão nova ou rotacionando uma existente.
 */
public final class SessionIssuer {

    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenCodec refreshTokenCodec;
    private final RefreshTokenRepository refreshTokens;
    private final Duration refreshTokenTtl;
    private final Clock clock;

    public SessionIssuer(AccessTokenIssuer accessTokenIssuer, RefreshTokenCodec refreshTokenCodec,
                         RefreshTokenRepository refreshTokens, Duration refreshTokenTtl, Clock clock) {
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenCodec = refreshTokenCodec;
        this.refreshTokens = refreshTokens;
        this.refreshTokenTtl = refreshTokenTtl;
        this.clock = clock;
    }

    public AuthenticatedSession startSession(User user) {
        String rawRefreshToken = refreshTokenCodec.generate();
        RefreshToken refreshToken = RefreshToken.startSession(
                user.id(), refreshTokenCodec.hash(rawRefreshToken), clock.instant(), refreshTokenTtl);
        refreshTokens.save(refreshToken);
        return toSession(user, rawRefreshToken, refreshToken);
    }

    public AuthenticatedSession rotateSession(User user, RefreshToken current) {
        String rawRefreshToken = refreshTokenCodec.generate();
        Instant now = clock.instant();
        RefreshToken next = current.rotate(refreshTokenCodec.hash(rawRefreshToken), now, refreshTokenTtl);
        refreshTokens.save(current);
        refreshTokens.save(next);
        return toSession(user, rawRefreshToken, next);
    }

    private AuthenticatedSession toSession(User user, String rawRefreshToken, RefreshToken refreshToken) {
        AccessTokenIssuer.IssuedAccessToken accessToken = accessTokenIssuer.issue(user);
        return new AuthenticatedSession(
                accessToken.value(), accessToken.expiresAt(), rawRefreshToken, refreshToken.expiresAt());
    }
}
