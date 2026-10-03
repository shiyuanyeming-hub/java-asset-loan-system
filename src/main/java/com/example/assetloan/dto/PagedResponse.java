package com.example.assetloan.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * 一覧 API の共通レスポンス（Spring の {@code Page} をそのまま返さないための薄いラッパー）。
 *
 * <p>{@code Page} を直接シリアライズすると Spring Data の内部構造がそのまま API 仕様になってしまい、
 * 将来のバージョンアップで壊れるため、必要な情報だけを持つ独自の形にしている。
 */
public record PagedResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <E, T> PagedResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PagedResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
