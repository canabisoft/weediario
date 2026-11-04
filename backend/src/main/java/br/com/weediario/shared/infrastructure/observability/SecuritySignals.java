package br.com.weediario.shared.infrastructure.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.LongAdder;

/**
 * Sinais agregados para perceber um ataque em andamento sem guardar quem fez o quê: só contadores por tipo de evento,
 * em memória, sem IP, sem conta e sem conteúdo. A cada hora sai uma linha de resumo no log (rotacionado pelo docker)
 * e um aviso para cada sinal acima do normal; depois os contadores zeram.
 * <p>
 * Para acompanhar: {@code docker compose logs backend | grep "Sinais de segurança"}, ou de fora, pelo
 * {@link #report() relatório} que o monitor externo consulta (só nomes fixos e números, nenhum texto de usuário).
 */
@Component
public class SecuritySignals {

    private static final Logger log = LoggerFactory.getLogger(SecuritySignals.class);

    /** @param alertPerHour a partir de quantos na hora o resumo vira aviso (bem acima do uso normal) */
    public enum Signal {
        LOGIN_FAILED("senha errada", 300),
        LOGIN_THROTTLED("login barrado pelo limite de tentativas", 100),
        HASHING_SATURATED("pedido recusado com o Argon2 no teto", 20),
        REGISTRATIONS("cadastro", 200),
        CHALLENGE_REJECTED("desafio anti-robô recusado", 50),
        QUOTA_EXCEEDED("cota por conta estourada", 100),
        SESSION_REVOKED("token de sessão encerrada", 100),
        REPORTS("denúncia", 100),
        AUTO_HIDDEN("conteúdo ocultado por denúncias", 10),
        IMAGES_REJECTED("foto recusada", 100);

        private final String label;
        private final long alertPerHour;

        Signal(String label, long alertPerHour) {
            this.label = label;
            this.alertPerHour = alertPerHour;
        }
    }

    /** Uma hora fechada (ou a corrente, com {@code to} nulo). */
    public record Window(Instant from, Instant to, Map<Signal, Long> counts) {
    }

    /** Sinal que passou do limite, numa das duas janelas. */
    public record Alert(Signal signal, String label, long count, long alertAt, Instant windowFrom) {
    }

    /**
     * O que o monitor externo lê: "alert" quando algum sinal passou do limite na hora corrente ou na última. Um
     * {@code upSince} diferente da leitura anterior quer dizer que o backend reiniciou (e zerou a hora corrente).
     */
    public record Report(String status, Instant upSince, Window currentHour, Window lastHour, List<Alert> alerts) {
    }

    private final Map<Signal, LongAdder> counters = new EnumMap<>(Signal.class);
    private final Clock clock;
    private final Instant upSince;
    private volatile Instant currentFrom;
    private volatile Window lastHour;

    public SecuritySignals() {
        this(Clock.systemUTC());
    }

    SecuritySignals(Clock clock) {
        this.clock = clock;
        this.upSince = clock.instant();
        this.currentFrom = upSince;
        for (Signal signal : Signal.values()) {
            counters.put(signal, new LongAdder());
        }
    }

    public void record(Signal signal) {
        counters.get(signal).increment();
    }

    /** Contagem desde o último resumo (para testes e diagnóstico). */
    public long count(Signal signal) {
        return counters.get(signal).sum();
    }

    public Report report() {
        Map<Signal, Long> now = new LinkedHashMap<>();
        counters.forEach((signal, counter) -> now.put(signal, counter.sum()));
        Window current = new Window(currentFrom, null, now);
        Window previous = lastHour;

        List<Alert> alerts = new ArrayList<>(alertsIn(current));
        if (previous != null) {
            alerts.addAll(alertsIn(previous));
        }
        return new Report(alerts.isEmpty() ? "ok" : "alert", upSince, current, previous, List.copyOf(alerts));
    }

    @Scheduled(cron = "0 0 * * * *")
    void hourlySummary() {
        Map<Signal, Long> closed = drain();
        if (closed.values().stream().allMatch(count -> count == 0)) {
            return;
        }
        StringJoiner summary = new StringJoiner(", ");
        closed.forEach((signal, count) -> {
            if (count > 0) {
                summary.add(signal.label + "=" + count);
            }
        });
        log.info("Sinais de segurança (última hora): {}", summary);
        closed.forEach((signal, count) -> {
            if (count >= signal.alertPerHour) {
                log.warn("Sinais de segurança: {} acima do normal ({} na última hora; aviso a partir de {})",
                        signal.label, count, signal.alertPerHour);
            }
        });
    }

    /** Fecha a hora corrente: devolve a contagem, guarda como "última hora" e zera. */
    Map<Signal, Long> drain() {
        Instant to = clock.instant();
        Map<Signal, Long> snapshot = new EnumMap<>(Signal.class);
        counters.forEach((signal, counter) -> snapshot.put(signal, counter.sumThenReset()));
        lastHour = new Window(currentFrom, to, Collections.unmodifiableMap(new EnumMap<>(snapshot)));
        currentFrom = to;
        return snapshot;
    }

    private static List<Alert> alertsIn(Window window) {
        List<Alert> alerts = new ArrayList<>();
        window.counts().forEach((signal, count) -> {
            if (count >= signal.alertPerHour) {
                alerts.add(new Alert(signal, signal.label, count, signal.alertPerHour, window.from()));
            }
        });
        return alerts;
    }
}
