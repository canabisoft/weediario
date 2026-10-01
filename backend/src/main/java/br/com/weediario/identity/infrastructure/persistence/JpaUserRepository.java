package br.com.weediario.identity.infrastructure.persistence;

import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.model.UserId;
import br.com.weediario.identity.domain.model.Username;
import br.com.weediario.identity.domain.repository.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaUserRepository implements UserRepository {

    private final SpringDataUserRepository jpa;

    public JpaUserRepository(SpringDataUserRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public User save(User user) {
        return jpa.save(UserJpaEntity.fromDomain(user)).toDomain();
    }

    @Override
    public Optional<User> findById(UserId id) {
        return jpa.findById(id.value()).map(UserJpaEntity::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpa.findByUsernameIgnoreCase(username).map(UserJpaEntity::toDomain);
    }

    @Override
    public boolean existsByUsername(Username username) {
        return jpa.existsByUsernameIgnoreCase(username.value());
    }
}
