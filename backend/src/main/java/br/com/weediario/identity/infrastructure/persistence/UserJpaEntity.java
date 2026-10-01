package br.com.weediario.identity.infrastructure.persistence;

import br.com.weediario.identity.domain.model.AccessKeyHash;
import br.com.weediario.identity.domain.model.AccountStatus;
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

    @Column(name = "access_key_hash", length = 64)
    private String accessKeyHash;

    @Column(name = "legacy_password_hash")
    private String legacyPasswordHash;

    @Column(name = "recovery_hash")
    private String recoveryHash;

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

    @Column(name = "terms_version", length = 10)
    private String termsVersion;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Column(name = "profile_hidden", nullable = false)
    private boolean profileHidden;

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
        entity.accessKeyHash = user.accessKeyHash().map(AccessKeyHash::value).orElse(null);
        entity.legacyPasswordHash = user.legacyPasswordHash().map(PasswordHash::value).orElse(null);
        entity.recoveryHash = user.recoveryHash().map(PasswordHash::value).orElse(null);
        entity.role = user.role();
        entity.status = user.status();
        entity.bio = user.bio().orElse(null);
        entity.avatarImageId = user.avatarImageId().orElse(null);
        entity.termsVersion = user.termsVersion().orElse(null);
        entity.termsAcceptedAt = user.termsAcceptedAt().orElse(null);
        entity.profileHidden = user.profileHidden();
        entity.createdAt = user.createdAt();
        entity.updatedAt = user.updatedAt();
        return entity;
    }

    User toDomain() {
        return User.restore(new UserId(id), new Username(username),
                accessKeyHash == null ? null : new AccessKeyHash(accessKeyHash),
                legacyPasswordHash == null ? null : new PasswordHash(legacyPasswordHash),
                recoveryHash == null ? null : new PasswordHash(recoveryHash), role, status, bio, avatarImageId,
                termsVersion, termsAcceptedAt, profileHidden, createdAt, updatedAt);
    }
}
