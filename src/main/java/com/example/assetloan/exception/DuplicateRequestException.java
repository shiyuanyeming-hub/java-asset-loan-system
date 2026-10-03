package com.example.assetloan.exception;

/**
 * 同じ対象に対する重複した申請（HTTP 409）。
 *
 * <p>例: すでに申請中の備品に対して、別の社員（または同じ社員）が申請した。
 */
public class DuplicateRequestException extends BusinessException {

    public DuplicateRequestException(String message) {
        super("DUPLICATE_REQUEST", message);
    }
}
