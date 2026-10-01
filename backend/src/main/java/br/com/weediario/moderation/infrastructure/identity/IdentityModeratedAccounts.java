package br.com.weediario.moderation.infrastructure.identity;

import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.model.UserId;
import br.com.weediario.identity.domain.repository.RefreshTokenRepository;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.moderation.application.port.ModeratedAccounts;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Tradução para o contexto de identidade: muda status (encerrando as sessões abertas) e papel das contas. */
@Component
public class IdentityModeratedAccounts implements ModeratedAccounts {

    private final UserRepository users;
    private final RefreshTokenRepository sessions;
    private final Clock clock;

    public IdentityModeratedAccounts(UserRepository users, RefreshTokenRepository sessions, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Override
    public Optional<Account> find(UUID userId) {
        return users.findById(new UserId(userId)).map(IdentityModeratedAccounts::account);
    }

    @Override
    public Optional<Account> findByUsername(String username) {
        return users.findByUsername(username).map(IdentityModeratedAccounts::account);
    }

    @Override
    public void suspend(UUID userId) {
        change(userId, User::suspend);
        sessions.revokeAllOfUser(userId, clock.instant());
    }

    @Override
    public void ban(UUID userId) {
        change(userId, User::ban);
        sessions.revokeAllOfUser(userId, clock.instant());
    }

    @Override
    public void reinstate(UUID userId) {
        change(userId, User::reinstate);
    }

    @Override
    public List<Account> staff() {
        return users.findStaff().stream().map(IdentityModeratedAccounts::account).toList();
    }

    /**
     * Sem encerrar sessões: o papel vai no access token, e o filtro de status recusa o token antigo na próxima
     * requisição; o cliente renova a sessão e recebe o papel novo.
     */
    @Override
    public void promoteToModerator(UUID userId) {
        change(userId, User::promoteToModerator);
    }

    @Override
    public void removeFromTeam(UUID userId) {
        change(userId, User::removeFromTeam);
    }

    private void change(UUID userId, BiConsumer<User, Clock> action) {
        users.findById(new UserId(userId)).ifPresent(user -> {
            action.accept(user, clock);
            users.save(user);
        });
    }

    private static Account account(User user) {
        return new Account(user.id().value(), user.username().value(), user.status().name(), user.role().name());
    }
}
