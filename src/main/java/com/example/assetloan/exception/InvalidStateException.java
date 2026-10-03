package com.example.assetloan.exception;

/**
 * 現在の状態では実行できない操作（HTTP 409）。
 *
 * <p>例: 貸出中の備品をもう一度申請した / 返却済みの貸出を却下した。
 * 「リクエストの形式は正しいが、今の状態と矛盾している」ケースを表す。
 */
public class InvalidStateException extends BusinessException {

    public InvalidStateException(String message) {
        super("INVALID_STATE", message);
    }
}
