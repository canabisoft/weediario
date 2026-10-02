package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.exception.InvalidCredentialsException;
import br.com.weediario.identity.domain.model.AccessKey;
import br.com.weediario.identity.domain.model.PasswordHash;
import br.com.weediario.identity.domain.model.RecoveryVerifier;
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
 * Exige a chave de acesso atual mesmo com a sessão aberta: com só um token roubado, dava para trocar a frase e depois
 * tomar a conta pela recuperação. Sem limite de tentativas: a chave tem 256 bits.
 */
public final class ChangeRecoveryPhraseUseCase {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ChangeRecoveryPhraseUseCase(UserRepository users, PasswordHasher hasher, UnitOfWork unitOfWork,
                                       Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public void execute(UUID userId, String recoveryVerifier, String currentAccessKey) {
        RecoveryVerifier verifier = new RecoveryVerifier(recoveryVerifier);
        User user = load(new UserId(userId));

        boolean matches = AccessKey.isWellFormed(currentAccessKey)
                && user.accessKeyMatches(new AccessKey(currentAccessKey).hash());
        if (!matches) {
            throw new InvalidCredentialsException("Chave de acesso atual incorreta");
        }

        PasswordHash recoveryHash = hasher.hash(verifier.value());
        unitOfWork.run(() -> {
            User fresh = users.findById(user.id()).orElseThrow(() -> new ResourceNotFoundException("Usuário"));
            fresh.changeRecovery(recoveryHash, clock);
            users.save(fresh);
        });
    }

    private User load(UserId id) {
        return unitOfWork.execute(() -> users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuário")));
    }
}
