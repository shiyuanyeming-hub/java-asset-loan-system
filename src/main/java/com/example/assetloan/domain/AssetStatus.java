package com.example.assetloan.domain;

/**
 * 備品の状態。
 *
 * <p>在庫として貸し出せるのは {@link #AVAILABLE} のみ。貸出中・修理中・廃棄済みは申請できない。
 */
public enum AssetStatus {
    /** 貸出可能。 */
    AVAILABLE,
    /** 貸出中。返却されると AVAILABLE に戻る。 */
    LOANED,
    /** 修理・点検中。貸出対象外（管理者が状態を変更する）。 */
    MAINTENANCE,
    /** 廃棄済み。論理削除の代わりにこの状態を使い、履歴からは参照できるようにする。 */
    RETIRED
}
