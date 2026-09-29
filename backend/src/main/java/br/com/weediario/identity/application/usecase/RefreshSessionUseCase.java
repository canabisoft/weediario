package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.AuthenticatedSession;
import br.com.weediario.identity.application.SessionIssuer;
import br.com.weediario.identity.application.exception.InvalidSessionException;
import br.com.weediario.identity.application.port.RefreshTokenCodec;
import br.com.weediario.identity.domain.model.RefreshToken;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.repository.RefreshTokenRepository;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.shared.application.UnitOfWork;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Troca um refresh token válido por um novo par de tokens (rotação).
 * Reuso de token já rotacionado é tratado como roubo e revoga a sessão inteira.
 */
public final class RefreshSessionUseCase {

    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final RefreshTokenCodec refreshTokenCodec;
    private final SessionIssuer sessionIssuer;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public RefreshSessionUseCase(RefreshTokenRepository refreshTokens, UserRepository users,
                                 RefreshTokenCodec refreshTokenCodec, SessionIssuer sessionIssuer,
                                 UnitOfWork unitOfWork, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.refreshTokenCodec = refreshTokenCodec;
        this.sessionIssuer = sessionIssuer;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public AuthenticatedSession execute(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidSessionException();
        }
        String tokenHash = refreshTokenCodec.hash(rawRefreshToken);

        // A falha é sinalizada por Optional vazio (e não por exceção) para que a revogação por reuso seja commitada.
        return unitOfWork.execute(() -> refreshTokens.findByTokenHash(tokenHash).flatMap(this::rotate))
                .orElseThrow(InvalidSessionException::new);
    }

    private Optional<AuthenticatedSession> rotate(RefreshToken current) {
        Instant now = clock.instant();
        if (current.isRevoked()) {
            refreshTokens.revokeFamily(current.familyId(), now);
            return Optional.empty();
        }
        if (current.isExpired(now)) {
            return Optional.empty();
        }
        return users.findById(current.userId())
                .filter(User::canAuthenticate)
                .map(user -> sessionIssuer.rotateSession(user, current));
    }
}
