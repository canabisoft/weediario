package br.com.weediario.shared.infrastructure.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.LongAdder;

/**
 * Sinais agregados para perceber um ataque em andamento sem guardar quem fez o quê: só contadores por tipo de evento,
 * em memória, sem IP, sem conta e sem conteúdo. A cada hora sai uma linha de resumo no log (rotacionado pelo docker)
 * e um aviso para cada sinal acima do normal; depois os contadores zeram.
 * <p>
 * Para acompanhar: {@code docker compose logs backend | grep "Sinais de segurança"}.
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

    private final Map<Signal, LongAdder> counters = new EnumMap<>(Signal.class);

    public SecuritySignals() {
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

    @Scheduled(cron = "0 0 * * * *")
    void hourlySummary() {
        Map<Signal, Long> lastHour = drain();
        if (lastHour.values().stream().allMatch(count -> count == 0)) {
            return;
        }
        StringJoiner summary = new StringJoiner(", ");
        lastHour.forEach((signal, count) -> {
            if (count > 0) {
                summary.add(signal.label + "=" + count);
            }
        });
        log.info("Sinais de segurança (última hora): {}", summary);
        lastHour.forEach((signal, count) -> {
            if (count >= signal.alertPerHour) {
                log.warn("Sinais de segurança: {} acima do normal ({} na última hora; aviso a partir de {})",
                        signal.label, count, signal.alertPerHour);
            }
        });
    }

    Map<Signal, Long> drain() {
        Map<Signal, Long> snapshot = new EnumMap<>(Signal.class);
        counters.forEach((signal, counter) -> snapshot.put(signal, counter.sumThenReset()));
        return snapshot;
    }
}
