package br.com.weediario.identity.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptsTest {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    @Test
    void locksAfterFiveFailuresInTheWindow() {
        LoginAttempts attempts = LoginAttempts.none(NOW);
        for (int i = 0; i < 4; i++) {
            attempts = attempts.registerFailure(NOW.plusSeconds(i));
        }
        assertThat(attempts.lockRemaining(NOW.plusSeconds(5))).isEmpty();

        attempts = attempts.registerFailure(NOW.plusSeconds(5));

        assertThat(attempts.lockRemaining(NOW.plusSeconds(5))).contains(Duration.ofMinutes(15));
        assertThat(attempts.lockRemaining(NOW.plusSeconds(5).plus(Duration.ofMinutes(15)))).isEmpty();
    }

    @Test
    void failuresOutsideTheWindowStartOver() {
        LoginAttempts attempts = LoginAttempts.none(NOW);
        for (int i = 0; i < 4; i++) {
            attempts = attempts.registerFailure(NOW);
        }

        attempts = attempts.registerFailure(NOW.plus(Duration.ofMinutes(20)));

        assertThat(attempts.failures()).isEqualTo(1);
        assertThat(attempts.lockRemaining(NOW.plus(Duration.ofMinutes(20)))).isEmpty();
    }

    @Test
    void repeatedLocksDoubleUpToTheCap() {
        LoginAttempts attempts = LoginAttempts.none(NOW);
        Instant at = NOW;
        Duration lastLock = Duration.ZERO;
        for (int lock = 1; lock <= 7; lock++) {
            for (int i = 0; i < LoginAttempts.MAX_FAILURES; i++) {
                attempts = attempts.registerFailure(at);
            }
            lastLock = attempts.lockRemaining(at).orElseThrow();
            at = attempts.lockedUntil().plusSeconds(1);
        }

        assertThat(lastLock).isEqualTo(LoginAttempts.MAX_LOCK);
    }
}
