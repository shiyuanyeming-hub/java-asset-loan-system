package com.example.assetloan.domain;

/**
 * 社員の権限。承認・却下・返却処理などの管理者操作は {@link #ADMIN} のみ実行できる。
 */
public enum Role {
    /** 一般社員。備品の検索・申請・自分の貸出一覧の参照ができる。 */
    EMPLOYEE,
    /** 管理者。備品マスタ管理と貸出の承認・却下・返却処理ができる。 */
    ADMIN
}
