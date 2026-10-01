package br.com.weediario.identity.application.exception;

/**
 * Mensagem única para "usuário inexistente", "senha errada" e "conta suspensa", evitando enumeração de contas.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        this("Apelido ou senha inválidos");
    }

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
