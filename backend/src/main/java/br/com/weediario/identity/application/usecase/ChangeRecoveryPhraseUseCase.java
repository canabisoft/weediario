package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.exception.InvalidCredentialsException;
import br.com.weediario.identity.application.exception.LoginThrottledException;
import br.com.weediario.identity.application.port.LoginKeyHasher;
import br.com.weediario.identity.domain.model.LoginAttempts;
import br.com.weediario.identity.domain.model.PasswordHash;
import br.com.weediario.identity.domain.model.RecoveryVerifier;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.model.UserId;
import br.com.weediario.identity.domain.repository.LoginAttemptsRepository;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.identity.domain.service.PasswordHasher;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Gera a primeira frase de recuperação (contas anteriores à frase) ou troca por uma nova (a antiga deixa de valer).
 * <p>
 * Exige a senha atual mesmo com a sessão aberta: com só um token roubado, dava para trocar a frase e depois tomar a
 * conta pela recuperação. Erros de senha aqui contam no mesmo limite de tentativas do login.
 */
public final class ChangeRecoveryPhraseUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final LoginAttemptsRepository attempts;
    private final LoginKeyHasher keyHasher;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ChangeRecoveryPhraseUseCase(UserRepository users, PasswordHasher passwordHasher,
                                       LoginAttemptsRepository attempts, LoginKeyHasher keyHasher,
                                       UnitOfWork unitOfWork, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.attempts = attempts;
        this.keyHasher = keyHasher;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public void execute(UUID userId, String recoveryVerifier, String currentPassword) {
        RecoveryVerifier verifier = new RecoveryVerifier(recoveryVerifier);
        User user = unitOfWork.execute(() -> users.findById(new UserId(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Usuário")));

        // Mesma chave do login (apelido normalizado): o limite vale para os dois caminhos.
        String attemptKey = keyHasher.keyOf(user.username().value().toLowerCase(Locale.ROOT));
        Instant now = clock.instant();
        LoginAttempts current = unitOfWork.execute(() -> attempts.find(attemptKey)).orElse(LoginAttempts.none(now));
        current.lockRemaining(now).ifPresent(remaining -> {
            throw new LoginThrottledException(remaining);
        });
        if (!passwordHasher.matches(currentPassword == null ? "" : currentPassword, user.passwordHash())) {
            unitOfWork.run(() -> attempts.save(attemptKey, current.registerFailure(clock.instant())));
            throw new InvalidCredentialsException("Senha atual incorreta");
        }

        PasswordHash recoveryHash = passwordHasher.hash(verifier.value());
        unitOfWork.run(() -> {
            User fresh = users.findById(user.id()).orElseThrow(() -> new ResourceNotFoundException("Usuário"));
            fresh.changeRecovery(recoveryHash, clock);
            users.save(fresh);
        });
    }
}
