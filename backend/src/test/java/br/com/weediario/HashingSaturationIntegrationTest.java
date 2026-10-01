package br.com.weediario;

import br.com.weediario.identity.infrastructure.security.HashingLimiter;
import br.com.weediario.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teto global de Argon2: com todas as vagas ocupadas, os endpoints que fazem Argon2 respondem 429 com Retry-After
 * (sem IP, sem contar tentativa errada), enquanto o login com chave de acesso, que não usa Argon2, segue normal.
 */
class HashingSaturationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private HashingLimiter limiter;

    @Test
    void saturatedHashingAnswers429WithRetryAfterAndRecoversAfterwards() throws Exception {
        Session grower = newGrower();
        long attemptsBefore = jdbc.queryForObject("select count(*) from login_attempts", Long.class);

        int permits = limiter.maxConcurrent();
        CountDownLatch holding = new CountDownLatch(permits);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(permits);
        try {
            for (int i = 0; i < permits; i++) {
                pool.submit(() -> limiter.call(() -> {
                    holding.countDown();
                    try {
                        release.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                }));
            }
            assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();

            mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"%s","recoveryVerifier":"%s"}""".formatted(grower.username(), VERIFIER)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "2"))
                    .andExpect(jsonPath("$.detail").value(containsString("Tente de novo em alguns segundos")));
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(registerJson("satur_" + System.nanoTime() % 1_000_000)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

            // Login com chave de acesso é SHA-256: não passa pelo teto.
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson(grower.username(), grower.accessKey())))
                    .andExpect(status().isOk());
        } finally {
            release.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        // Saturação não conta como tentativa errada (o login acima, se algo tivesse ficado, teria limpado o seu).
        assertThat(jdbc.queryForObject("select count(*) from login_attempts", Long.class)).isLessThanOrEqualTo(attemptsBefore);
        mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","recoveryVerifier":"%s"}""".formatted(grower.username(), VERIFIER)))
                .andExpect(status().isOk());
    }
}
