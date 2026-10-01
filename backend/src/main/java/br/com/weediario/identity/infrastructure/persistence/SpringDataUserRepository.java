package br.com.weediario.identity.infrastructure.persistence;

import br.com.weediario.identity.domain.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    List<UserJpaEntity> findByRoleInOrderByUsernameAsc(Collection<Role> roles);
}
