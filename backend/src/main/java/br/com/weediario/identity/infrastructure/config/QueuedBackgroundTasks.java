package br.com.weediario.identity.infrastructure.config;

import br.com.weediario.identity.application.port.BackgroundTasks;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Uma fila pequena e uma thread: o que entra aqui é leve (consultar a conta, gravar um token, enfileirar um e-mail).
 * Fila cheia descarta a tarefa em vez de crescer sem limite; quem pede de novo tenta outra vez.
 */
@Component
public class QueuedBackgroundTasks implements BackgroundTasks {

    private static final Logger log = LoggerFactory.getLogger(QueuedBackgroundTasks.class);

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1_000), runnable -> {
                Thread thread = new Thread(runnable, "account-tasks");
                thread.setDaemon(true);
                return thread;
            }, (runnable, pool) -> log.warn("Fila de tarefas da conta cheia: uma tarefa foi descartada"));

    @Override
    public void submit(Runnable task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                // Sem a mensagem, que pode trazer dado de usuário.
                log.warn("Tarefa da conta falhou: {}", e.getClass().getSimpleName());
            }
        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
