package com.lavarapido.notification.domain.model;

import java.util.List;
import java.util.function.Function;

/** Una página de resultados sin depender de Spring Data en el dominio. */
public record PageResult<T>(List<T> items, int page, int size, long totalElements) {

    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), page, size, totalElements);
    }
}
