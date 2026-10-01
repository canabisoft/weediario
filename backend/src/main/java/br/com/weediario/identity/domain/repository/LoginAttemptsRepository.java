package br.com.weediario.identity.domain.repository;

import br.com.weediario.identity.domain.model.LoginAttempts;

import java.time.Instant;
import java.util.Optional;

/**
 * @see br.com.weediario.identity.application.port.LoginKeyHasher a chave nunca é o apelido em texto puro
 */
public interface LoginAttemptsRepository {

    Optional<LoginAttempts> find(String key);

    void save(String key, LoginAttempts attempts);

    void delete(String key);

    int deleteStaleBefore(Instant cutoff);
}
