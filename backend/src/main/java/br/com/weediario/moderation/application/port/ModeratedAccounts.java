package br.com.weediario.moderation.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contas vistas pela moderação: suspender, banir ou reativar (as sessões abertas são encerradas na hora) e, para o
 * admin, a equipe (promover a moderador ou remover da equipe).
 */
public interface ModeratedAccounts {

    Optional<Account> find(UUID userId);

    Optional<Account> findByUsername(String username);

    void suspend(UUID userId);

    void ban(UUID userId);

    void reinstate(UUID userId);

    /** Moderadores e admins. */
    List<Account> staff();

    void promoteToModerator(UUID userId);

    void removeFromTeam(UUID userId);

    /** @param role GROWER, MODERATOR ou ADMIN */
    record Account(UUID id, String username, String status, String role) {

        /** Moderador ou admin. */
        public boolean staff() {
            return !"GROWER".equals(role);
        }

        public boolean admin() {
            return "ADMIN".equals(role);
        }
    }
}
