package com.example.assetloan.dto;

import com.example.assetloan.domain.AssetStatus;

/**
 * 備品検索の条件。指定された項目だけが絞り込みに使われる（null は「条件なし」）。
 *
 * @param keyword  管理番号・名称・メーカー・型番の部分一致（大文字小文字を区別しない）
 * @param category カテゴリ ID
 * @param status   備品の状態
 */
public record AssetSearchCondition(
        String keyword,
        Long category,
        AssetStatus status
) {
}
