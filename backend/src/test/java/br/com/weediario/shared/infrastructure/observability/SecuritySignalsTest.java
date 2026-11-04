package br.com.weediario.shared.infrastructure.observability;

import br.com.weediario.shared.infrastructure.observability.SecuritySignals.Signal;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SecuritySignalsTest {

    @Test
    void theHourlySummaryDrainsTheCounters() {
        SecuritySignals signals = new SecuritySignals();
        signals.record(Signal.LOGIN_FAILED);
        signals.record(Signal.LOGIN_FAILED);
        signals.record(Signal.AUTO_HIDDEN);

        assertThat(signals.drain())
                .containsEntry(Signal.LOGIN_FAILED, 2L)
                .containsEntry(Signal.AUTO_HIDDEN, 1L)
                .containsEntry(Signal.REGISTRATIONS, 0L);
        assertThat(signals.count(Signal.LOGIN_FAILED)).isZero();
    }

    @Test
    void theReportIsOkWhileEverySignalStaysBelowItsLimit() {
        SecuritySignals signals = new SecuritySignals();
        signals.record(Signal.LOGIN_FAILED);

        SecuritySignals.Report report = signals.report();

        assertThat(report.status()).isEqualTo("ok");
        assertThat(report.alerts()).isEmpty();
        assertThat(report.currentHour().counts()).containsEntry(Signal.LOGIN_FAILED, 1L);
        assertThat(report.lastHour()).isNull();
    }

    @Test
    void aSignalOverTheLimitAlertsInTheCurrentHourAndKeepsAlertingForTheNextOne() {
        Instant start = Instant.parse("2026-11-04T20:00:00Z");
        SecuritySignals signals = new SecuritySignals(Clock.fixed(start, ZoneOffset.UTC));
        for (int i = 0; i < 10; i++) {
            signals.record(Signal.AUTO_HIDDEN);
        }

        assertThat(signals.report().status()).isEqualTo("alert");
        assertThat(signals.report().alerts()).singleElement().satisfies(alert -> {
            assertThat(alert.signal()).isEqualTo(Signal.AUTO_HIDDEN);
            assertThat(alert.count()).isEqualTo(10);
            assertThat(alert.alertAt()).isEqualTo(10);
            assertThat(alert.windowFrom()).isEqualTo(start);
        });

        signals.hourlySummary();
        SecuritySignals.Report afterTheHour = signals.report();

        assertThat(afterTheHour.status()).isEqualTo("alert");
        assertThat(afterTheHour.currentHour().counts()).containsEntry(Signal.AUTO_HIDDEN, 0L);
        assertThat(afterTheHour.lastHour().counts()).containsEntry(Signal.AUTO_HIDDEN, 10L);
        assertThat(afterTheHour.upSince()).isEqualTo(start);

        signals.hourlySummary();
        assertThat(signals.report().status()).isEqualTo("ok");
    }
}
