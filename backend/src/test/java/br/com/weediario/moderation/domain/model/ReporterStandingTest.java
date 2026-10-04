package br.com.weediario.moderation.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ReporterStandingTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");
    private static final Instant WEEK_AGO = NOW.minus(ReporterStanding.MIN_ACCOUNT_AGE);

    @Test
    void oldAccountWithRealUseCounts() {
        assertThat(new ReporterStanding(WEEK_AGO, false, true).countsTowardHiding(NOW)).isTrue();
        assertThat(new ReporterStanding(WEEK_AGO, true, false).countsTowardHiding(NOW)).isTrue();
    }

    @Test
    void newAccountDoesNotCountEvenWithActivity() {
        Instant sixDaysAgo = WEEK_AGO.plus(Duration.ofDays(1));
        assertThat(new ReporterStanding(sixDaysAgo, true, true).countsTowardHiding(NOW)).isFalse();
    }

    @Test
    void oldAccountWithoutAnyUseDoesNotCount() {
        assertThat(new ReporterStanding(NOW.minus(Duration.ofDays(365)), false, false).countsTowardHiding(NOW)).isFalse();
    }
}
