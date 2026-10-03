package com.example.assetloan.web;

import com.example.assetloan.exception.BusinessException;
import com.example.assetloan.exception.ForbiddenOperationException;
import com.example.assetloan.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 例外 → HTTP レスポンスの変換。
 *
 * <p>RFC 9457（Problem Details for HTTP APIs）の形式で返すので、
 * クライアントは {@code status} と {@code code} を見て分岐できる。
 *
 * <pre>
 * {
 *   "type": "about:blank",
 *   "title": "Conflict",
 *   "status": 409,
 *   "detail": "この備品には既に貸出申請があります（申請者: 佐藤 花子 / 状態: REQUESTED）",
 *   "instance": "/api/loans",
 *   "code": "DUPLICATE_REQUEST"
 * }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 業務例外（404 / 403 / 409 系）。 */
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException e) {
        HttpStatus status = switch (e) {
            case ResourceNotFoundException ignored -> HttpStatus.NOT_FOUND;
            case ForbiddenOperationException ignored -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.CONFLICT;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", e.getCode());
        return problem;
    }

    /** 楽観ロックの衝突（同時更新）。利用者は再実行すれば解決する。 */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException e) {
        log.warn("optimistic lock conflict: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "他の操作と競合しました。最新の状態を確認して、もう一度実行してください。");
        problem.setTitle(HttpStatus.CONFLICT.getReasonPhrase());
        problem.setProperty("code", "CONCURRENT_UPDATE");
        return problem;
    }

    /** DB の一意制約違反など（アプリ側のチェックをすり抜けた最終防衛線）。 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("data integrity violation: {}", e.getMostSpecificCause().getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "データの制約に違反しました（重複した値がある可能性があります）。");
        problem.setTitle(HttpStatus.CONFLICT.getReasonPhrase());
        problem.setProperty("code", "DATA_INTEGRITY_VIOLATION");
        return problem;
    }

    /** @Valid の検証エラー。どの項目が悪いかをまとめて返す。 */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fieldError -> errors.put(fieldError.getField(), fieldError.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "入力値が不正です。");
        problem.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        problem.setProperty("code", "VALIDATION_ERROR");
        problem.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }
}
