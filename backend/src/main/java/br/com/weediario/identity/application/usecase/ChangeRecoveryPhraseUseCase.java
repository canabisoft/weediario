package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.CredentialCheck;
import br.com.weediario.identity.application.SecurityNotifier;
import br.com.weediario.identity.domain.model.PasswordHash;
import br.com.weediario.identity.domain.model.RecoveryVerifier;
import br.com.weediario.identity.domain.model.SecurityEvent;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.model.UserId;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.identity.domain.service.PasswordHasher;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;

import java.time.Clock;
import java.util.UUID;

/**
 * Gera a primeira frase de recuperação (contas anteriores à frase) ou troca por uma nova (a antiga deixa de valer).
 * <p>
 * Exige a senha atual mesmo com a sessão aberta: com só um token roubado, dava para trocar a frase e depois tomar a
 * conta pela recuperação. Os erros contam no limite de tentativas do login (ver {@link CredentialCheck}).
 */
public final class ChangeRecoveryPhraseUseCase {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final CredentialCheck credentials;
    private final SecurityNotifier security;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ChangeRecoveryPhraseUseCase(UserRepository users, PasswordHasher hasher, CredentialCheck credentials,
                                       SecurityNotifier security, UnitOfWork unitOfWork, Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.credentials = credentials;
        this.security = security;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    /** @param currentPassword senha atual (ou, em conta que ainda não criou senha, a chave de acesso antiga) */
    public void execute(UUID userId, String recoveryVerifier, String currentPassword) {
        RecoveryVerifier verifier = new RecoveryVerifier(recoveryVerifier);
        User user = load(new UserId(userId));
        credentials.require(user, currentPassword);

        PasswordHash recoveryHash = hasher.hash(verifier.value());
        unitOfWork.run(() -> {
            User fresh = load(user.id());
            fresh.changeRecovery(recoveryHash, clock);
            users.save(fresh);
        });
        security.recorded(user, SecurityEvent.Kind.RECOVERY_PHRASE_CHANGED);
    }

    private User load(UserId id) {
        return unitOfWork.execute(() -> users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuário")));
    }
}
