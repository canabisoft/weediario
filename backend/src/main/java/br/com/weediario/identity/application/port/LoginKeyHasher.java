package br.com.weediario.identity.application.port;

/**
 * Transforma o apelido digitado no login (ou na recuperação) em uma chave não reversível para o controle de
 * tentativas, para que a tabela de tentativas não seja uma lista de contas.
 */
public interface LoginKeyHasher {

    String keyOf(String normalizedLogin);
}
