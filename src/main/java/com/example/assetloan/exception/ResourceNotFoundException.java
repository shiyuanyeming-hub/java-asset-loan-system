package com.example.assetloan.exception;

/** 指定された ID のリソースが存在しない（HTTP 404）。 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resource, Object id) {
        super("RESOURCE_NOT_FOUND", "%s が見つかりません: id=%s".formatted(resource, id));
    }

    public ResourceNotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", message);
    }
}
