package com.example.assetloan.domain;

/**
 * 貸出申請の状態。想定する遷移は次の通り。
 *
 * <pre>
 * REQUESTED ──承認──▶ APPROVED ──返却──▶ RETURNED
 *     │                    │
 *     └──却下──▶ REJECTED   └──申請者による取消──▶ CANCELED
 * </pre>
 *
 * <p>上記以外の遷移（例: REJECTED から APPROVED）はサービス層で例外にする。
 */
public enum LoanStatus {
    /** 申請中。管理者の承認待ち。 */
    REQUESTED,
    /** 承認済み（貸出中）。備品の状態も LOANED になる。 */
    APPROVED,
    /** 却下。備品は貸出可能のまま。 */
    REJECTED,
    /** 返却済み。備品は貸出可能に戻る。 */
    RETURNED,
    /** 申請者による取消（承認前のみ）。 */
    CANCELED;

    /** 備品を占有している状態かどうか（重複申請の判定に使う）。 */
    public boolean holdsAsset() {
        return this == REQUESTED || this == APPROVED;
    }
}
