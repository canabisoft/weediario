package br.com.weediario.shared.application;

import java.time.Duration;

/**
 * 429 com {@code Retry-After}: a conta usou toda a cota dessa ação na janela atual.
 */
public class QuotaExceededException extends RuntimeException {

    private final Duration retryAfter;

    public QuotaExceededException(Duration retryAfter) {
        super("Você chegou ao limite dessa ação por agora. Tente de novo mais tarde.");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
