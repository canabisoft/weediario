package br.com.weediario.identity.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    private static final Duration TTL = Duration.ofDays(7);
    private final Instant login = Instant.parse("2026-01-01T00:00:00Z");
    private final UserId user = new UserId(UUID.randomUUID());

    @Test
    void rotationRenewsTheValidityButNeverPastTheMaximumSessionAge() {
        RefreshToken token = RefreshToken.startSession(user, "a".repeat(64), login, TTL);

        RefreshToken nextDay = token.rotate("b".repeat(64), login.plus(Duration.ofDays(1)), TTL);
        assertThat(nextDay.expiresAt()).isEqualTo(login.plus(Duration.ofDays(8)));
        assertThat(nextDay.familyStartedAt()).isEqualTo(login);

        RefreshToken nearTheEnd = nextDay.rotate("c".repeat(64), login.plus(Duration.ofDays(88)), TTL);
        assertThat(nearTheEnd.expiresAt()).isEqualTo(login.plus(RefreshToken.MAX_SESSION_AGE));
        assertThat(nearTheEnd.isExpired(login.plus(RefreshToken.MAX_SESSION_AGE))).isTrue();
    }
}
