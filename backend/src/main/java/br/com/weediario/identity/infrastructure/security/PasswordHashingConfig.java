package br.com.weediario.identity.infrastructure.security;

import br.com.weediario.identity.domain.service.PasswordHasher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** A porta {@link PasswordHasher} entregue aos casos de uso é o Argon2 já atrás do teto de concorrência. */
@Configuration
public class PasswordHashingConfig {

    @Bean
    HashingLimiter hashingLimiter(PasswordHashingProperties properties) {
        return new HashingLimiter(properties.maxConcurrent(), properties.maxWait());
    }

    @Bean
    PasswordHasher passwordHasher(HashingLimiter limiter) {
        return new LimitedPasswordHasher(new Argon2PasswordHasher(), limiter);
    }
}
