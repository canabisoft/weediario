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
 * Privacidade por design: não existem (e não devem ser adicionados) campos de IP, geolocalização, cidade/estado,
 * user-agent ou data de último acesso. Nome real também não é coletado. O e-mail é opcional e mora fora do agregado
 * ({@link AccountEmail}), cifrado, só para recuperar a senha e receber avisos de segurança.
 * <p>
 * Entra-se com apelido + senha (Argon2id). A frase de 12 palavras recupera a conta (o servidor só conhece o hash de um
 * derivado dela). Contas criadas na época da {@link AccessKey chave de acesso} entram uma última vez com ela e criam
 * uma senha. A foto de perfil é opcional e passa pelo mesmo sanitizador de metadados das fotos dos diários.
 */
public final class User {

    public static final int BIO_MAX_LENGTH = 280;

    private final UserId id;
    private final Username username;
    /** Argon2id da senha; nulo só em contas da época da chave de acesso que ainda não criaram senha. */
    private PasswordHash passwordHash;
    /** Chave de acesso antiga (transição): só existe em contas que ainda não criaram senha. */
    private AccessKeyHash accessKeyHash;
    /** Argon2 do derivado da frase de recuperação; nulo em contas anteriores à frase (geram no próximo login). */
    private PasswordHash recoveryHash;
    private Role role;
    private AccountStatus status;
    private String bio;
    private UUID avatarImageId;
    private String termsVersion;
    private Instant termsAcceptedAt;
    /** Bio e foto ocultadas do público pela moderação (o dono continua vendo). */
    private boolean profileHidden;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(UserId id, Username username, PasswordHash passwordHash, AccessKeyHash accessKeyHash,
                 PasswordHash recoveryHash,
                 Role role, AccountStatus status, String bio, UUID avatarImageId, String termsVersion,
                 Instant termsAcceptedAt, boolean profileHidden, Instant createdAt, Instant updatedAt) {
        this.id = Guard.notNull(id, "Id");
        this.username = Guard.notNull(username, "Nome de usuário");
        if (passwordHash == null && accessKeyHash == null && recoveryHash == null) {
            throw new DomainException("Conta sem credencial de acesso");
        }
        this.passwordHash = passwordHash;
        this.accessKeyHash = accessKeyHash;
        this.recoveryHash = recoveryHash;
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

    /**
     * Só cria a conta com o aceite da versão vigente dos Termos de Uso (versão e data/hora ficam registradas) e com a
     * frase de recuperação já gerada: o e-mail é opcional, então a frase é o jeito de recuperar que toda conta tem.
     */
    public static User register(Username username, PasswordHash passwordHash, PasswordHash recoveryHash,
                                String acceptedTermsVersion, Clock clock) {
        if (username.isReserved()) {
            throw new DomainException("Nome de usuário indisponível");
        }
        String terms = TermsOfUse.requireCurrent(acceptedTermsVersion);
        Instant now = clock.instant();
        Guard.notNull(passwordHash, "Senha");
        Guard.notNull(recoveryHash, "Frase de recuperação");
        return new User(UserId.newId(), username, passwordHash, null, recoveryHash, Role.GROWER, AccountStatus.ACTIVE, null, null,
                terms, now, false, now, now);
    }

    /** Reconstitui um usuário já persistido; não aplica regras de criação. */
    public static User restore(UserId id, Username username, PasswordHash passwordHash,
                               AccessKeyHash accessKeyHash, PasswordHash recoveryHash,
                               Role role, AccountStatus status, String bio, UUID avatarImageId,
                               String termsVersion, Instant termsAcceptedAt, boolean profileHidden,
                               Instant createdAt, Instant updatedAt) {
        return new User(id, username, passwordHash, accessKeyHash, recoveryHash, role, status, bio, avatarImageId, termsVersion,
                termsAcceptedAt, profileHidden, createdAt, updatedAt);
    }

    public boolean canAuthenticate() {
        return status == AccountStatus.ACTIVE;
    }

    /** Comparação em tempo constante; conta sem chave (já tem senha) nunca confere. */
    public boolean accessKeyMatches(AccessKeyHash candidate) {
        return accessKeyHash != null && accessKeyHash.matches(candidate);
    }

    /**
     * Define a senha (troca, redefinição ou a primeira senha de uma conta da época da chave). A chave de acesso
     * antiga, se ainda havia, deixa de valer. Quem chama já conferiu a senha atual, a frase ou o link do e-mail.
     */
    public void changePassword(PasswordHash newHash, Clock clock) {
        this.passwordHash = Guard.notNull(newHash, "Senha");
        this.accessKeyHash = null;
        this.updatedAt = clock.instant();
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    /** Define (ou troca) a frase de recuperação. Quem chama já conferiu a senha atual. */
    public void changeRecovery(PasswordHash newRecoveryHash, Clock clock) {
        this.recoveryHash = Guard.notNull(newRecoveryHash, "Frase de recuperação");
        this.updatedAt = clock.instant();
    }

    public boolean hasRecovery() {
        return recoveryHash != null;
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

    /**
     * Gestão da equipe (só admin, conferido no caso de uso): um grower ativo vira moderador. Admins são definidos
     * fora do app (no banco), para nenhuma falha na API conseguir criar um admin.
     */
    public void promoteToModerator(Clock clock) {
        if (role != Role.GROWER) {
            throw new DomainException("A conta já faz parte da equipe");
        }
        if (status != AccountStatus.ACTIVE) {
            throw new DomainException("Só contas ativas podem entrar na equipe");
        }
        this.role = Role.MODERATOR;
        this.updatedAt = clock.instant();
    }

    /** Moderador volta a ser grower. Admins não saem da equipe pela API (evita ficar sem nenhum admin). */
    public void removeFromTeam(Clock clock) {
        if (role != Role.MODERATOR) {
            throw new DomainException(role == Role.ADMIN
                    ? "Admins não são removidos pelo app"
                    : "A conta não faz parte da equipe");
        }
        this.role = Role.GROWER;
        this.updatedAt = clock.instant();
    }

    public boolean isStaff() {
        return role != Role.GROWER;
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

    public Optional<PasswordHash> passwordHash() {
        return Optional.ofNullable(passwordHash);
    }

    public Optional<AccessKeyHash> accessKeyHash() {
        return Optional.ofNullable(accessKeyHash);
    }

    public Optional<PasswordHash> recoveryHash() {
        return Optional.ofNullable(recoveryHash);
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
