package br.com.weediario.social.infrastructure.identity;

import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.social.application.port.GrowerLookup;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class IdentityGrowerLookup implements GrowerLookup {

    private final UserRepository users;

    public IdentityGrowerLookup(UserRepository users) {
        this.users = users;
    }

    @Override
    public Optional<UUID> activeGrowerId(String username) {
        return users.findByUsername(username).filter(User::canAuthenticate).map(user -> user.id().value());
    }
}
