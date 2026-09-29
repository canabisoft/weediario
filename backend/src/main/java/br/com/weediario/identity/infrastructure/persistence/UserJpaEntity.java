package br.com.weediario.identity.infrastructure.persistence;

import br.com.weediario.identity.domain.model.AccountStatus;
import br.com.weediario.identity.domain.model.Email;
import br.com.weediario.identity.domain.model.PasswordHash;
import br.com.weediario.identity.domain.model.Role;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.model.UserId;
import br.com.weediario.identity.domain.model.Username;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 30)
    private String username;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(length = User.BIO_MAX_LENGTH)
    private String bio;

    @Column(name = "avatar_image_id")
    private UUID avatarImageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserJpaEntity() {
    }

    static UserJpaEntity fromDomain(User user) {
        UserJpaEntity entity = new UserJpaEntity();
        entity.id = user.id().value();
        entity.username = user.username().value();
        entity.email = user.email().value();
        entity.passwordHash = user.passwordHash().value();
        entity.role = user.role();
        entity.status = user.status();
        entity.bio = user.bio().orElse(null);
        entity.avatarImageId = user.avatarImageId().orElse(null);
        entity.createdAt = user.createdAt();
        entity.updatedAt = user.updatedAt();
        return entity;
    }

    User toDomain() {
        return User.restore(new UserId(id), new Username(username), new Email(email), new PasswordHash(passwordHash),
                role, status, bio, avatarImageId, createdAt, updatedAt);
    }
}
