package com.bereta.admin;

import org.springframework.data.domain.Page;
import java.util.List;

public record AdminPageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
    static <S, T> AdminPageResponse<T> from(Page<S> page, java.util.function.Function<S, T> mapper) {
        return new AdminPageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
