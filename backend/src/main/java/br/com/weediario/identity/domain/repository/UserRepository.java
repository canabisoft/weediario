package br.com.weediario.identity.domain.repository;

import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.model.UserId;
import br.com.weediario.identity.domain.model.Username;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UserId id);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(Username username);

    /** Moderadores e admins, em ordem de apelido. */
    List<User> findStaff();

    /**
     * Apaga a conta. O banco leva junto o que é dela ({@code on delete cascade}); registros que precisam ficar (moderação,
     * itens do catálogo aberto) só perdem o vínculo ({@code on delete set null}).
     */
    void delete(UserId id);
}
