package br.com.weediario.identity.domain.repository;

import br.com.weediario.identity.domain.model.LoginAttempts;

import java.time.Instant;

/**
 * @see br.com.weediario.identity.application.port.LoginKeyHasher a chave nunca é o apelido em texto puro
 */
public interface LoginAttemptsRepository {

    /**
     * Lê as tentativas da chave travando a linha até o fim da transação (cria a linha zerada se ainda não existir).
     * Duas requisições ao mesmo tempo para o mesmo apelido esperam uma pela outra, em vez de lerem o mesmo valor.
     */
    LoginAttempts findForUpdate(String key, Instant now);

    void save(String key, LoginAttempts attempts);

    void delete(String key);

    int deleteStaleBefore(Instant cutoff);
}
