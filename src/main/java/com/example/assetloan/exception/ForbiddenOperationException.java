package com.example.assetloan.exception;

/** 権限が足りない操作（HTTP 403）。例: 一般社員が承認しようとした。 */
public class ForbiddenOperationException extends BusinessException {

    public ForbiddenOperationException(String message) {
        super("FORBIDDEN_OPERATION", message);
    }
}
