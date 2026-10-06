package br.com.weediario.shared.application;

import br.com.weediario.shared.domain.DomainException;

/**
 * Paginação por offset com teto de tamanho e de profundidade, para que o cliente não consiga pedir páginas gigantes
 * nem um offset enorme (que faz o banco percorrer e descartar milhões de linhas a cada pedido).
 */
public record PageQuery(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;
    /** Bem acima de qualquer listagem real (o catálogo inteiro cabe em menos de 500 páginas de 20). */
    public static final int MAX_PAGE = 1_000;

    public PageQuery {
        page = Math.max(page, 0);
        size = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        if (page > MAX_PAGE) {
            throw new DomainException("Página além do limite. Use a busca ou os filtros para encontrar o que procura.");
        }
    }

    public long offset() {
        return (long) page * size;
    }
}
