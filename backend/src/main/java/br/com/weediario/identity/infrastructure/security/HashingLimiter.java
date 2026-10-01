package br.com.weediario.identity.infrastructure.security;

import br.com.weediario.identity.application.exception.HashingBusyException;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Semáforo justo (ordem de chegada) que limita quantos cálculos pesados rodam ao mesmo tempo no servidor inteiro.
 * Não guarda nada sobre quem pediu: nem IP, nem apelido, só a contagem de vagas.
 * <p>
 * Quem não consegue vaga em {@code maxWait} recebe {@link HashingBusyException} (429) na hora, em vez de empilhar
 * threads e memória. A vaga é devolvida sempre, inclusive quando o cálculo lança exceção.
 */
public class HashingLimiter {

    private final Semaphore permits;
    private final int maxConcurrent;
    private final Duration maxWait;
    private final Duration retryAfter;

    public HashingLimiter(int maxConcurrent, Duration maxWait) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("maxConcurrent precisa ser pelo menos 1");
        }
        this.permits = new Semaphore(maxConcurrent, true);
        this.maxConcurrent = maxConcurrent;
        this.maxWait = maxWait;
        // Retry-After curto: a fila anda em milissegundos, então o próprio tempo de espera (arredondado) basta.
        this.retryAfter = Duration.ofSeconds(Math.max(1, (maxWait.toMillis() + 999) / 1000));
    }

    public <T> T call(Supplier<T> work) {
        acquire();
        try {
            return work.get();
        } finally {
            permits.release();
        }
    }

    public int maxConcurrent() {
        return maxConcurrent;
    }

    private void acquire() {
        try {
            if (!permits.tryAcquire(maxWait.toNanos(), TimeUnit.NANOSECONDS)) {
                throw new HashingBusyException(retryAfter);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new HashingBusyException(retryAfter);
        }
    }
}
