package com.lavarapido.notification.infrastructure.adapter.in.web.dto;

import com.lavarapido.notification.domain.model.PageResult;

import java.util.List;

/** Misma forma de página que usa el security-service (items, page, size, totalElements, totalPages). */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(PageResult<T> page) {
        return new PageResponse<>(page.items(), page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
