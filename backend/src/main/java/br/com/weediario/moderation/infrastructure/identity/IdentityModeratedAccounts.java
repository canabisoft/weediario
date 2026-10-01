package br.com.weediario.moderation.infrastructure.identity;

import br.com.weediario.identity.domain.model.Role;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.model.UserId;
import br.com.weediario.identity.domain.repository.RefreshTokenRepository;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.moderation.application.port.ModeratedAccounts;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Tradução para o contexto de identidade: muda o status da conta e encerra as sessões abertas. */
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

    private void change(UUID userId, BiConsumer<User, Clock> action) {
        users.findById(new UserId(userId)).ifPresent(user -> {
            action.accept(user, clock);
            users.save(user);
        });
    }

    private static Account account(User user) {
        return new Account(user.id().value(), user.username().value(), user.status().name(), user.role() != Role.GROWER);
    }
}
