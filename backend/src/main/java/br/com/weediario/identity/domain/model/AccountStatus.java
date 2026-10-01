package br.com.weediario.identity.domain.model;

/**
 * {@code SUSPENDED} é temporário (a moderação pode reativar); {@code BANNED} é o encerramento definitivo por
 * violação dos Termos de Uso. Nos dois casos a conta não entra e o conteúdo some do público.
 */
public enum AccountStatus {
    ACTIVE,
    SUSPENDED,
    BANNED
}
