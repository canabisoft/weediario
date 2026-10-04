package br.com.weediario;

import br.com.weediario.identity.application.exception.HashingBusyException;
import br.com.weediario.identity.application.exception.InvalidCredentialsException;
import br.com.weediario.identity.application.exception.LoginThrottledException;
import br.com.weediario.identity.application.usecase.AuthenticateUserUseCase;
import br.com.weediario.identity.domain.model.LoginAttempts;
import br.com.weediario.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Força bruta em paralelo: tentativas simultâneas para o mesmo apelido não podem ler o mesmo contador e contar como
 * uma só. Cada uma é reservada antes do Argon2, então no máximo {@link LoginAttempts#MAX_FAILURES} chegam a conferir
 * a senha e as demais já encontram o bloqueio.
 */
class LoginThrottleConcurrencyIntegrationTest extends IntegrationTestSupport {

    private static final int THREADS = 20;

    @Autowired
    private AuthenticateUserUseCase authenticate;

    @Test
    void parallelWrongPasswordsAllCountAndTheLockHolds() throws Exception {
        Session grower = newGrower();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<Class<?>>> outcomes = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                String guess = "chute errado numero " + i;
                outcomes.add(pool.submit(() -> {
                    start.await();
                    try {
                        authenticate.execute(new AuthenticateUserUseCase.Command(grower.username(), guess));
                        return Void.class;
                    } catch (RuntimeException e) {
                        return e.getClass();
                    }
                }));
            }
            start.countDown();
            List<Class<?>> results = new ArrayList<>();
            for (Future<Class<?>> outcome : outcomes) {
                results.add(outcome.get(60, TimeUnit.SECONDS));
            }
            Map<Class<?>, Long> counts = results.stream()
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            assertThat(counts.keySet()).as("%s", counts).isSubsetOf(InvalidCredentialsException.class,
                    LoginThrottledException.class, HashingBusyException.class);
            assertThat(counts.getOrDefault(InvalidCredentialsException.class, 0L)).as("%s", counts)
                    .isLessThanOrEqualTo(LoginAttempts.MAX_FAILURES);
            assertThat(counts.getOrDefault(LoginThrottledException.class, 0L)).as("%s", counts).isPositive();
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        // Nem a senha certa entra enquanto a espera não passa.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(grower.username(), grower.password())))
                .andExpect(status().isTooManyRequests());
    }
}
