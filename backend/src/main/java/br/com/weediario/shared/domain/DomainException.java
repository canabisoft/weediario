package br.com.weediario.shared.domain;

/**
 * Violação de regra de negócio. A mensagem é segura para ser exibida ao cliente.
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }
}
