package br.com.weediario;

import br.com.weediario.media.application.usecase.UploadImageUseCase;
import br.com.weediario.shared.application.ActionQuota;
import br.com.weediario.shared.application.ActionQuota.Limit;
import br.com.weediario.shared.application.QuotaExceededException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.support.IntegrationTestSupport;
import br.com.weediario.support.TestImages;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cotas por conta no banco: o teto vale mesmo com pedidos simultâneos, cada conta tem a sua, a janela recomeça
 * quando vence e o que a cota guarda não identifica ninguém.
 */
class ActionQuotaIntegrationTest extends IntegrationTestSupport {

    private static final int THREADS = 20;

    @Autowired
    private ActionQuota quota;

    @Autowired
    private UnitOfWork unitOfWork;

    @Autowired
    private UploadImageUseCase upload;

    @Test
    void theLimitHoldsUnderConcurrencyAndIsPerAccount() throws Exception {
        Limit limit = new Limit("teste-paralelo-" + UUID.randomUUID(), 5, Duration.ofHours(1));
        UUID account = UUID.randomUUID();

        List<Boolean> results = inParallel(() -> {
            try {
                unitOfWork.run(() -> quota.consume(account, limit));
                return true;
            } catch (QuotaExceededException e) {
                return false;
            }
        });

        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(5);
        unitOfWork.run(() -> quota.consume(UUID.randomUUID(), limit));
        assertThat(jdbc.queryForObject("select count(*) from action_quotas where quota_key like ?", Long.class,
                "%" + account + "%")).isZero();
    }

    @Test
    void theWindowStartsOverWhenItExpires() throws Exception {
        Limit limit = new Limit("teste-janela-" + UUID.randomUUID(), 1, Duration.ofMillis(300));
        UUID account = UUID.randomUUID();
        unitOfWork.run(() -> quota.consume(account, limit));
        assertThatThrownBy(() -> unitOfWork.run(() -> quota.consume(account, limit)))
                .isInstanceOf(QuotaExceededException.class);

        Thread.sleep(400);
        unitOfWork.run(() -> quota.consume(account, limit));
    }

    /** Antes, a contagem de imagens sem uso e a gravação eram passos separados: envios simultâneos passavam do teto. */
    @Test
    void parallelUploadsDoNotExceedThePendingCap() throws Exception {
        Session grower = newGrower();
        UUID owner = jdbc.queryForObject("select id from users where username = ?", UUID.class, grower.username());
        byte[] photo = TestImages.encode(TestImages.twoColor(64, 48), "png");
        for (int i = 0; i < UploadImageUseCase.MAX_PENDING_PER_OWNER - 2; i++) {
            upload.execute(owner, photo);
        }

        inParallel(() -> {
            try {
                upload.execute(owner, photo);
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        });

        assertThat(jdbc.queryForObject("select count(*) from images where owner_id = ? and status = 'PENDING'",
                Long.class, owner)).isEqualTo(UploadImageUseCase.MAX_PENDING_PER_OWNER);
    }

    private static <T> List<T> inParallel(Callable<T> task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }
}
