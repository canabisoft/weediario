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
    private String termsVersion;
    private Instant termsAcceptedAt;
    /** Bio e foto ocultadas do público pela moderação (o dono continua vendo). */
    private boolean profileHidden;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(UserId id, Username username, Email email, PasswordHash passwordHash,
                 Role role, AccountStatus status, String bio, UUID avatarImageId, String termsVersion,
                 Instant termsAcceptedAt, boolean profileHidden, Instant createdAt, Instant updatedAt) {
        this.id = Guard.notNull(id, "Id");
        this.username = Guard.notNull(username, "Nome de usuário");
        this.email = Guard.notNull(email, "E-mail");
        this.passwordHash = Guard.notNull(passwordHash, "Senha");
        this.role = Guard.notNull(role, "Perfil");
        this.status = Guard.notNull(status, "Status");
        this.bio = validBio(bio);
        this.avatarImageId = avatarImageId;
        this.termsVersion = termsVersion;
        this.termsAcceptedAt = termsAcceptedAt;
        this.profileHidden = profileHidden;
        this.createdAt = Guard.notNull(createdAt, "Data de criação");
        this.updatedAt = Guard.notNull(updatedAt, "Data de atualização");
    }

    /** Só cria a conta com o aceite da versão vigente dos Termos de Uso (versão e data/hora ficam registradas). */
    public static User register(Username username, Email email, PasswordHash passwordHash, String acceptedTermsVersion,
                                Clock clock) {
        if (username.isReserved()) {
            throw new DomainException("Nome de usuário indisponível");
        }
        String terms = TermsOfUse.requireCurrent(acceptedTermsVersion);
        Instant now = clock.instant();
        return new User(UserId.newId(), username, email, passwordHash, Role.GROWER, AccountStatus.ACTIVE, null, null,
                terms, now, false, now, now);
    }

    /** Reconstitui um usuário já persistido; não aplica regras de criação. */
    public static User restore(UserId id, Username username, Email email, PasswordHash passwordHash,
                               Role role, AccountStatus status, String bio, UUID avatarImageId,
                               String termsVersion, Instant termsAcceptedAt, boolean profileHidden,
                               Instant createdAt, Instant updatedAt) {
        return new User(id, username, email, passwordHash, role, status, bio, avatarImageId, termsVersion,
                termsAcceptedAt, profileHidden, createdAt, updatedAt);
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

    public void acceptTerms(String version, Clock clock) {
        this.termsVersion = TermsOfUse.requireCurrent(version);
        this.termsAcceptedAt = clock.instant();
        this.updatedAt = this.termsAcceptedAt;
    }

    public boolean hasAcceptedCurrentTerms() {
        return TermsOfUse.CURRENT_VERSION.equals(termsVersion);
    }

    public void suspend(Clock clock) {
        changeStatus(AccountStatus.SUSPENDED, clock);
    }

    public void ban(Clock clock) {
        changeStatus(AccountStatus.BANNED, clock);
    }

    public void reinstate(Clock clock) {
        changeStatus(AccountStatus.ACTIVE, clock);
    }

    private void changeStatus(AccountStatus newStatus, Clock clock) {
        if (role != Role.GROWER) {
            throw new DomainException("Contas da equipe não são moderadas por aqui");
        }
        this.status = newStatus;
        this.updatedAt = clock.instant();
    }

    /** Moderação: oculta (ou volta a mostrar) bio e foto para o público. */
    public void hideProfile(boolean hidden, Clock clock) {
        this.profileHidden = hidden;
        this.updatedAt = clock.instant();
    }

    /** Moderação: remove bio e foto. @return a foto removida, para o chamador apagar o arquivo */
    public Optional<UUID> clearProfile(Clock clock) {
        Optional<UUID> previous = Optional.ofNullable(avatarImageId);
        this.bio = null;
        this.avatarImageId = null;
        this.profileHidden = false;
        this.updatedAt = clock.instant();
        return previous;
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

    public Optional<String> termsVersion() {
        return Optional.ofNullable(termsVersion);
    }

    public Optional<Instant> termsAcceptedAt() {
        return Optional.ofNullable(termsAcceptedAt);
    }

    public boolean profileHidden() {
        return profileHidden;
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
