package br.com.weediario.moderation.application.port;

import java.util.Optional;
import java.util.UUID;

/** Suspender, banir ou reativar contas (as sessões abertas são encerradas na hora). */
public interface ModeratedAccounts {

    Optional<Account> find(UUID userId);

    Optional<Account> findByUsername(String username);

    void suspend(UUID userId);

    void ban(UUID userId);

    void reinstate(UUID userId);

    /** @param staff moderador ou admin (não é moderado por este fluxo) */
    record Account(UUID id, String username, String status, boolean staff) {
    }
}
