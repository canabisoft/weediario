package br.com.weediario.shared.application;

/**
 * Paginação por offset com teto de tamanho, para que o cliente não consiga pedir páginas gigantes.
 */
public record PageQuery(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    public PageQuery {
        page = Math.max(page, 0);
        size = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    public long offset() {
        return (long) page * size;
    }
}
