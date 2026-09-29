package br.com.weediario.identity.application.exception;

/**
 * Mensagem única para "usuário inexistente", "senha errada" e "conta suspensa", evitando enumeração de contas.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("E-mail ou senha inválidos");
    }
}
