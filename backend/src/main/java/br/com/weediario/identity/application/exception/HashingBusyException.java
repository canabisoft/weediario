package br.com.weediario.identity.application.exception;

import java.time.Duration;

/**
 * O servidor já está no teto de cálculos Argon2 simultâneos (cadastro, recuperação, login de conta antiga, troca de
 * frase). Não é culpa de ninguém em particular nem conta como tentativa errada: é só pedir de novo daqui a pouco.
 */
public class HashingBusyException extends RuntimeException {

    private final Duration retryAfter;

    public HashingBusyException(Duration retryAfter) {
        super("Muita gente entrando agora. Tente de novo em alguns segundos.");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
