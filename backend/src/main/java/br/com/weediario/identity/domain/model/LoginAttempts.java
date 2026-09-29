package br.com.weediario.identity.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Controle de força bruta por conta, sem IP: conta falhas de login para um e-mail dentro de uma janela.
 * <p>
 * Ao atingir {@link #MAX_FAILURES} falhas, o login fica bloqueado. Cada novo bloqueio dobra o tempo
 * (15 min, 30 min, 1 h...) até {@link #MAX_LOCK}. O bloqueio vale para qualquer e-mail, exista a conta ou não,
 * então não serve para descobrir quem está cadastrado.
 */
public record LoginAttempts(int failures, Instant windowStartedAt, Instant lockedUntil, int lockCount) {

    public static final int MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(15);
    public static final Duration BASE_LOCK = Duration.ofMinutes(15);
    public static final Duration MAX_LOCK = Duration.ofHours(6);

    public static LoginAttempts none(Instant now) {
        return new LoginAttempts(0, now, null, 0);
    }

    public Optional<Duration> lockRemaining(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil)
                ? Optional.of(Duration.between(now, lockedUntil))
                : Optional.empty();
    }

    public LoginAttempts registerFailure(Instant now) {
        boolean windowExpired = now.isAfter(windowStartedAt.plus(WINDOW));
        int currentFailures = (windowExpired ? 0 : failures) + 1;
        Instant windowStart = windowExpired ? now : windowStartedAt;
        if (currentFailures < MAX_FAILURES) {
            return new LoginAttempts(currentFailures, windowStart, lockedUntil, lockCount);
        }
        int locks = lockCount + 1;
        Duration lock = BASE_LOCK.multipliedBy(1L << Math.min(locks - 1, 10));
        return new LoginAttempts(0, now, now.plus(lock.compareTo(MAX_LOCK) > 0 ? MAX_LOCK : lock), locks);
    }
}
