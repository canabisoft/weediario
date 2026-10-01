package br.com.weediario.identity.infrastructure.security;

import br.com.weediario.identity.domain.model.PasswordHash;
import br.com.weediario.identity.domain.service.PasswordHasher;

/**
 * Decorator que passa todo {@code hash}/{@code matches} pelo {@link HashingLimiter}: os casos de uso continuam
 * falando só com a porta {@link PasswordHasher} e não sabem do teto. Eles já chamam o Argon2 fora da transação,
 * então quem espera vaga não segura conexão com o banco.
 * <p>
 * Os decoys de tempo calculados no construtor dos casos de uso também passam por aqui, mas na inicialização não há
 * concorrência e a vaga sai na hora.
 */
public class LimitedPasswordHasher implements PasswordHasher {

    private final PasswordHasher delegate;
    private final HashingLimiter limiter;

    public LimitedPasswordHasher(PasswordHasher delegate, HashingLimiter limiter) {
        this.delegate = delegate;
        this.limiter = limiter;
    }

    @Override
    public PasswordHash hash(String rawPassword) {
        return limiter.call(() -> delegate.hash(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash hash) {
        return limiter.call(() -> delegate.matches(rawPassword, hash));
    }
}
