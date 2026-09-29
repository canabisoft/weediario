package br.com.weediario.identity.application.port;

/**
 * Transforma o e-mail digitado no login em uma chave não reversível para o controle de tentativas,
 * para que a tabela de tentativas não seja uma lista de e-mails.
 */
public interface LoginKeyHasher {

    String keyOf(String normalizedEmail);
}
