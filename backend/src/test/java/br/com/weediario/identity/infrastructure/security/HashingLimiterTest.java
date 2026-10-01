package br.com.weediario.identity.infrastructure.security;

import br.com.weediario.identity.application.exception.HashingBusyException;
import br.com.weediario.identity.domain.model.PasswordHash;
import br.com.weediario.identity.domain.service.PasswordHasher;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Teto global de Argon2: com todas as vagas ocupadas, o próximo falha rápido; vagas sempre voltam. */
class HashingLimiterTest {

    private static final Duration WAIT = Duration.ofMillis(200);

    @Test
    void callBeyondTheCapFailsAfterTheWaitWithRetryAfter() throws Exception {
        HashingLimiter limiter = new HashingLimiter(2, WAIT);
        CountDownLatch holding = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> busy = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                busy.add(pool.submit(() -> limiter.call(() -> {
                    holding.countDown();
                    await(release);
                    return "ok";
                })));
            }
            assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();

            long start = System.nanoTime();
            assertThatThrownBy(() -> limiter.call(() -> "terceiro"))
                    .isInstanceOf(HashingBusyException.class)
                    .hasMessageContaining("Tente de novo em alguns segundos")
                    .satisfies(ex -> assertThat(((HashingBusyException) ex).retryAfter()).isEqualTo(Duration.ofSeconds(1)));
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(WAIT.minusMillis(20));

            release.countDown();
            for (Future<String> f : busy) {
                assertThat(f.get(5, TimeUnit.SECONDS)).isEqualTo("ok");
            }
            assertThat(limiter.call(() -> "depois")).isEqualTo("depois");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void waitingCallGetsThePermitWhenOneIsReleasedInTime() throws Exception {
        HashingLimiter limiter = new HashingLimiter(1, Duration.ofSeconds(5));
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> limiter.call(() -> {
                holding.countDown();
                await(release);
                return null;
            }));
            assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();
            new Thread(() -> {
                sleep(100);
                release.countDown();
            }).start();
            assertThat(limiter.call(() -> "na fila")).isEqualTo("na fila");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void permitIsReturnedEvenWhenTheWorkThrows() {
        HashingLimiter limiter = new HashingLimiter(1, WAIT);
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> limiter.call(() -> {
                throw new IllegalStateException("falhou");
            })).isInstanceOf(IllegalStateException.class);
        }
        assertThat(limiter.call(() -> "livre")).isEqualTo("livre");
    }

    @Test
    void decoratorRoutesHashAndMatchesThroughTheLimiter() {
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        PasswordHasher slow = new PasswordHasher() {
            @Override
            public PasswordHash hash(String raw) {
                track();
                return new PasswordHash("h:" + raw);
            }

            @Override
            public boolean matches(String raw, PasswordHash hash) {
                track();
                return hash.value().equals("h:" + raw);
            }

            private void track() {
                peak.accumulateAndGet(inside.incrementAndGet(), Math::max);
                sleep(20);
                inside.decrementAndGet();
            }
        };
        PasswordHasher limited = new LimitedPasswordHasher(slow, new HashingLimiter(2, Duration.ofSeconds(10)));

        AtomicInteger matched = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String raw = "x" + i;
            threads.add(Thread.ofVirtual().start(() -> {
                if (limited.matches(raw, limited.hash(raw))) {
                    matched.incrementAndGet();
                }
            }));
        }
        threads.forEach(HashingLimiterTest::join);
        assertThat(matched.get()).isEqualTo(8);
        assertThat(peak.get()).isBetween(1, 2);
    }

    @Test
    void automaticDefaultsKeepAtLeastTwoPermitsAndAShortWait() {
        PasswordHashingProperties defaults = new PasswordHashingProperties(0, null);
        assertThat(defaults.maxConcurrent()).isGreaterThanOrEqualTo(2);
        assertThat(defaults.maxWait()).isEqualTo(Duration.ofSeconds(2));
        assertThat(new PasswordHashingProperties(3, Duration.ofMillis(500)).maxConcurrent()).isEqualTo(3);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void join(Thread thread) {
        try {
            thread.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
