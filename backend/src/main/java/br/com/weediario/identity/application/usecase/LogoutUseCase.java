package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.port.RefreshTokenCodec;
import br.com.weediario.identity.domain.repository.RefreshTokenRepository;
import br.com.weediario.shared.application.UnitOfWork;

import java.time.Clock;

/**
 * Encerra a sessão revogando toda a família do refresh token. Idempotente.
 */
public final class LogoutUseCase {

    private final RefreshTokenRepository refreshTokens;
    private final RefreshTokenCodec refreshTokenCodec;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public LogoutUseCase(RefreshTokenRepository refreshTokens, RefreshTokenCodec refreshTokenCodec,
                         UnitOfWork unitOfWork, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.refreshTokenCodec = refreshTokenCodec;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public void execute(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String tokenHash = refreshTokenCodec.hash(rawRefreshToken);
        unitOfWork.run(() -> refreshTokens.findByTokenHash(tokenHash)
                .ifPresent(token -> refreshTokens.revokeFamily(token.familyId(), clock.instant())));
    }
}
