package br.com.weediario.shared.application;

import java.util.function.Supplier;

/**
 * Fronteira transacional dos casos de uso, sem acoplar a camada de aplicação ao Spring.
 */
public interface UnitOfWork {

    <T> T execute(Supplier<T> work);

    default void run(Runnable work) {
        execute(() -> {
            work.run();
            return null;
        });
    }
}
