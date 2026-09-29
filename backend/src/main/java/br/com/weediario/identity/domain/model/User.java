package br.com.weediario.identity.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Aggregate root do contexto de identidade.
 * <p>
 * Privacidade por design: não existem (e não devem ser adicionados) campos de IP, geolocalização,
 * cidade/estado, user-agent ou data de último acesso. Nome real também não é coletado.
 * A foto de perfil é opcional e passa pelo mesmo sanitizador de metadados das fotos dos diários.
 */
public final class User {

    public static final int BIO_MAX_LENGTH = 280;

    private final UserId id;
    private final Username username;
    private final Email email;
    private PasswordHash passwordHash;
    private final Role role;
    private AccountStatus status;
    private String bio;
    private UUID avatarImageId;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(UserId id, Username username, Email email, PasswordHash passwordHash,
                 Role role, AccountStatus status, String bio, UUID avatarImageId,
                 Instant createdAt, Instant updatedAt) {
        this.id = Guard.notNull(id, "Id");
        this.username = Guard.notNull(username, "Nome de usuário");
        this.email = Guard.notNull(email, "E-mail");
        this.passwordHash = Guard.notNull(passwordHash, "Senha");
        this.role = Guard.notNull(role, "Perfil");
        this.status = Guard.notNull(status, "Status");
        this.bio = validBio(bio);
        this.avatarImageId = avatarImageId;
        this.createdAt = Guard.notNull(createdAt, "Data de criação");
        this.updatedAt = Guard.notNull(updatedAt, "Data de atualização");
    }

    public static User register(Username username, Email email, PasswordHash passwordHash, Clock clock) {
        if (username.isReserved()) {
            throw new DomainException("Nome de usuário indisponível");
        }
        Instant now = clock.instant();
        return new User(UserId.newId(), username, email, passwordHash, Role.GROWER, AccountStatus.ACTIVE, null, null,
                now, now);
    }

    /** Reconstitui um usuário já persistido; não aplica regras de criação. */
    public static User restore(UserId id, Username username, Email email, PasswordHash passwordHash,
                               Role role, AccountStatus status, String bio, UUID avatarImageId,
                               Instant createdAt, Instant updatedAt) {
        return new User(id, username, email, passwordHash, role, status, bio, avatarImageId, createdAt, updatedAt);
    }

    public boolean canAuthenticate() {
        return status == AccountStatus.ACTIVE;
    }

    public void changePassword(PasswordHash newHash, Clock clock) {
        this.passwordHash = Guard.notNull(newHash, "Senha");
        this.updatedAt = clock.instant();
    }

    public void updateBio(String newBio, Clock clock) {
        this.bio = validBio(newBio);
        this.updatedAt = clock.instant();
    }

    /** @return a foto anterior, que deve ser apagada pelo chamador */
    public Optional<UUID> replaceAvatar(UUID newAvatarImageId, Clock clock) {
        Optional<UUID> previous = Optional.ofNullable(avatarImageId);
        this.avatarImageId = newAvatarImageId;
        this.updatedAt = clock.instant();
        return previous;
    }

    public void suspend(Clock clock) {
        this.status = AccountStatus.SUSPENDED;
        this.updatedAt = clock.instant();
    }

    public UserId id() {
        return id;
    }

    public Username username() {
        return username;
    }

    public Email email() {
        return email;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public Role role() {
        return role;
    }

    public AccountStatus status() {
        return status;
    }

    public Optional<String> bio() {
        return Optional.ofNullable(bio);
    }

    public Optional<UUID> avatarImageId() {
        return Optional.ofNullable(avatarImageId);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /** Texto livre guardado como veio (o Angular faz o escape); só removemos caracteres de controle. */
    private static String validBio(String bio) {
        if (bio == null || bio.isBlank()) {
            return null;
        }
        String cleaned = bio.replaceAll("[\\p{Cntrl}&&[^\n]]", "").strip();
        if (cleaned.length() > BIO_MAX_LENGTH) {
            throw new DomainException("A bio deve ter no máximo " + BIO_MAX_LENGTH + " caracteres");
        }
        return cleaned;
    }
}
