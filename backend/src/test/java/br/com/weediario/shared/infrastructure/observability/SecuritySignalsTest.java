package br.com.weediario.shared.infrastructure.observability;

import br.com.weediario.shared.infrastructure.observability.SecuritySignals.Signal;
import org.junit.jupiter.api.Test;

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
}
