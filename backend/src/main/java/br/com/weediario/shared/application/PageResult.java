package br.com.weediario.shared.application;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResult<T> of(List<T> items, PageQuery query, long totalItems) {
        int totalPages = (int) Math.ceil((double) totalItems / query.size());
        return new PageResult<>(List.copyOf(items), query.page(), query.size(), totalItems, totalPages);
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), page, size, totalItems, totalPages);
    }
}
