package br.com.weediario.identity.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Dificuldade e prazo do desafio do cadastro.
 *
 * @param maxNumber maior número possível da solução: o navegador testa, em média, metade disso (150 mil ≈ 1 s num
 *                  celular). Subir dobra o custo para quem cadastra em série e para as pessoas também.
 * @param ttl       quanto tempo um desafio vale depois de emitido
 */
@ConfigurationProperties(prefix = "weediario.identity.register-challenge")
public record RegistrationChallengeProperties(Long maxNumber, Duration ttl) {

    static final long DEFAULT_MAX_NUMBER = 150_000;
    static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    public RegistrationChallengeProperties {
        if (maxNumber == null || maxNumber < 1) {
            maxNumber = DEFAULT_MAX_NUMBER;
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            ttl = DEFAULT_TTL;
        }
    }
}
