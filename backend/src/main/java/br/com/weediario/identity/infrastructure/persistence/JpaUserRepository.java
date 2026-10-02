package br.com.weediario.identity.infrastructure.persistence;

import br.com.weediario.identity.domain.model.Role;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.model.UserId;
import br.com.weediario.identity.domain.model.Username;
import br.com.weediario.identity.domain.repository.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaUserRepository implements UserRepository {

    private final SpringDataUserRepository jpa;

    public JpaUserRepository(SpringDataUserRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    /** Com flush: e-mail, links e histórico são gravados por JDBC na mesma transação e referenciam a conta. */
    public User save(User user) {
        return jpa.saveAndFlush(UserJpaEntity.fromDomain(user)).toDomain();
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

    @Override
    public List<User> findStaff() {
        return jpa.findByRoleInOrderByUsernameAsc(List.of(Role.MODERATOR, Role.ADMIN)).stream()
                .map(UserJpaEntity::toDomain).toList();
    }

    @Override
    public void delete(UserId id) {
        jpa.deleteById(id.value());
        jpa.flush();
    }
}
