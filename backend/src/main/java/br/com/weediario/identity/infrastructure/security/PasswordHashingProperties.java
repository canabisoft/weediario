package br.com.weediario.identity.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Teto de cálculos Argon2 simultâneos. Os endpoints que fazem Argon2 são públicos e o limite de tentativas é por
 * apelido (nunca por IP), então variar apelidos bastaria para pôr o servidor a calcular hashes sem parar; o teto
 * global segura CPU e memória sem identificar quem pediu.
 *
 * @param maxConcurrent quantos Argon2 rodam ao mesmo tempo; 0 ou ausente = automático
 *                      ({@code max(2, núcleos / 2)}, para sobrar CPU para o resto da aplicação)
 * @param maxWait       quanto um pedido espera na fila por uma vaga antes de receber 429; ausente = 2 s
 */
@ConfigurationProperties(prefix = "weediario.identity.password-hashing")
public record PasswordHashingProperties(Integer maxConcurrent, Duration maxWait) {

    static final Duration DEFAULT_MAX_WAIT = Duration.ofSeconds(2);

    public PasswordHashingProperties {
        if (maxConcurrent == null || maxConcurrent <= 0) {
            maxConcurrent = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        }
        if (maxWait == null || maxWait.isNegative()) {
            maxWait = DEFAULT_MAX_WAIT;
        }
    }
}
