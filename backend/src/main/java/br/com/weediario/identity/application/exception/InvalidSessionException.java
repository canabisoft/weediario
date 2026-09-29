package br.com.weediario.identity.application.exception;

public class InvalidSessionException extends RuntimeException {

    public InvalidSessionException() {
        super("Sessão inválida ou expirada");
    }
}
