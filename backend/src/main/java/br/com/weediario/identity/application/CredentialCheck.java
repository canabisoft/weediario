package br.com.weediario.identity.application;

import br.com.weediario.identity.application.exception.HashingBusyException;
import br.com.weediario.identity.application.exception.InvalidCredentialsException;
import br.com.weediario.identity.domain.model.AccessKey;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.service.PasswordHasher;

/**
 * Confirmação da senha atual antes de mudanças sensíveis feitas com a sessão aberta (trocar senha, e-mail ou frase).
 * Sem ela, quem roubasse só um token de sessão poderia trocar o e-mail e depois tomar a conta pela recuperação.
 * <p>
 * Conta que ainda não criou senha (só tem a chave de acesso antiga) confirma com a chave. Os erros contam no mesmo
 * limite de tentativas do login: a senha é escolhida por gente e pode ser adivinhada.
 */
public final class CredentialCheck {

    private final PasswordHasher hasher;
    private final LoginThrottle throttle;

    public CredentialCheck(PasswordHasher hasher, LoginThrottle throttle) {
        this.hasher = hasher;
        this.throttle = throttle;
    }

    public void require(User user, String currentSecret) {
        LoginThrottle.Attempt attempt = throttle.begin(LoginThrottle.LOGIN, user.username().value());
        String secret = currentSecret == null ? "" : currentSecret;
        boolean matches;
        try {
            matches = user.passwordHash()
                    .map(hash -> hasher.matches(secret, hash))
                    .orElseGet(() -> AccessKey.isWellFormed(secret) && user.accessKeyMatches(new AccessKey(secret).hash()));
        } catch (HashingBusyException busy) {
            throttle.released(attempt);
            throw busy;
        }
        if (!matches) {
            throw new InvalidCredentialsException("Senha atual incorreta");
        }
        throttle.succeeded(attempt);
    }
}
