package br.com.weediario.diary.infrastructure.identity;

import br.com.weediario.diary.application.port.GrowerDirectory;
import br.com.weediario.diary.domain.model.GrowerId;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.model.UserId;
import br.com.weediario.identity.domain.repository.UserRepository;
import org.springframework.stereotype.Component;

/**
 * Adapter que traduz o conceito de "grower" do contexto de diários para o {@code User} do contexto de identidade.
 * É o único ponto em que os dois bounded contexts se tocam.
 */
@Component
public class IdentityGrowerDirectory implements GrowerDirectory {

    private final UserRepository users;

    public IdentityGrowerDirectory(UserRepository users) {
        this.users = users;
    }

    @Override
    public boolean isActiveGrower(GrowerId growerId) {
        return users.findById(new UserId(growerId.value()))
                .map(User::canAuthenticate)
                .orElse(false);
    }
}
