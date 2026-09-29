package br.com.weediario.identity.application.exception;

import java.time.Duration;

public class LoginThrottledException extends RuntimeException {

    private final Duration retryAfter;

    public LoginThrottledException(Duration retryAfter) {
        super("Muitas tentativas de login. Tente novamente em " + Math.max(1, retryAfter.toMinutes() + 1) + " minutos.");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
