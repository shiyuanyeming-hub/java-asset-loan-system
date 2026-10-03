package com.example.assetloan.exception;

/**
 * 業務ルール違反を表す例外の基底クラス。
 *
 * <p>{@code code} は API のレスポンス（RFC 9457 Problem Details の {@code code}）にそのまま出るので、
 * クライアント側で分岐に使える安定した文字列にする。
 */
public abstract class BusinessException extends RuntimeException {

    private final String code;

    protected BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
