package br.com.weediario.identity.domain.repository;

import br.com.weediario.identity.domain.model.AccountToken;
import br.com.weediario.identity.domain.model.UserId;

import java.time.Instant;
import java.util.Optional;

public interface AccountTokenRepository {

    void save(AccountToken token);

    /** Com lock: dois cliques no mesmo link não usam o token duas vezes. */
    Optional<AccountToken> findByHash(String tokenHash);

    /** Momento do último token emitido para a conta com essa finalidade (limite de envio de e-mails). */
    Optional<Instant> lastIssuedAt(UserId userId, AccountToken.Purpose purpose);

    /** Invalida os links pendentes (senha trocada, e-mail trocado...). */
    void deletePending(UserId userId, AccountToken.Purpose purpose);

    void deleteExpiredBefore(Instant instant);
}
