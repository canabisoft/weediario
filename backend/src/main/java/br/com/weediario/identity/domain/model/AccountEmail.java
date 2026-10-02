package br.com.weediario.identity.domain.model;

import br.com.weediario.shared.domain.Guard;

import java.time.Instant;
import java.util.Optional;

/**
 * E-mail (opcional) de uma conta. Só é usado para recuperar a senha depois de confirmado: um endereço digitado errado
 * mandaria o link de redefinição para um estranho.
 */
public final class AccountEmail {

    private final UserId userId;
    private final EmailAddress address;
    private Instant verifiedAt;

    private AccountEmail(UserId userId, EmailAddress address, Instant verifiedAt) {
        this.userId = Guard.notNull(userId, "Usuário");
        this.address = Guard.notNull(address, "E-mail");
        this.verifiedAt = verifiedAt;
    }

    public static AccountEmail unverified(UserId userId, EmailAddress address) {
        return new AccountEmail(userId, address, null);
    }

    public static AccountEmail restore(UserId userId, EmailAddress address, Instant verifiedAt) {
        return new AccountEmail(userId, address, verifiedAt);
    }

    public void verify(Instant now) {
        if (verifiedAt == null) {
            verifiedAt = now;
        }
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public UserId userId() {
        return userId;
    }

    public EmailAddress address() {
        return address;
    }

    public Optional<Instant> verifiedAt() {
        return Optional.ofNullable(verifiedAt);
    }
}
