package br.com.weediario.identity.domain.model;

import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;

/**
 * Aggregate root do contexto de identidade.
 * <p>
 * Privacidade por design: não existem (e não devem ser adicionados) campos de IP, geolocalização,
 * cidade/estado, user-agent ou data de último acesso. Nome real também não é coletado.
 */
public final class User {

    private final UserId id;
    private final Username username;
    private final Email email;
    private PasswordHash passwordHash;
    private final Role role;
    private AccountStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(UserId id, Username username, Email email, PasswordHash passwordHash,
                 Role role, AccountStatus status, Instant createdAt, Instant updatedAt) {
        this.id = Guard.notNull(id, "Id");
        this.username = Guard.notNull(username, "Nome de usuário");
        this.email = Guard.notNull(email, "E-mail");
        this.passwordHash = Guard.notNull(passwordHash, "Senha");
        this.role = Guard.notNull(role, "Perfil");
        this.status = Guard.notNull(status, "Status");
        this.createdAt = Guard.notNull(createdAt, "Data de criação");
        this.updatedAt = Guard.notNull(updatedAt, "Data de atualização");
    }

    public static User register(Username username, Email email, PasswordHash passwordHash, Clock clock) {
        Instant now = clock.instant();
        return new User(UserId.newId(), username, email, passwordHash, Role.GROWER, AccountStatus.ACTIVE, now, now);
    }

    /** Reconstitui um usuário já persistido; não aplica regras de criação. */
    public static User restore(UserId id, Username username, Email email, PasswordHash passwordHash,
                               Role role, AccountStatus status, Instant createdAt, Instant updatedAt) {
        return new User(id, username, email, passwordHash, role, status, createdAt, updatedAt);
    }

    public boolean canAuthenticate() {
        return status == AccountStatus.ACTIVE;
    }

    public void changePassword(PasswordHash newHash, Clock clock) {
        this.passwordHash = Guard.notNull(newHash, "Senha");
        this.updatedAt = clock.instant();
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

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
