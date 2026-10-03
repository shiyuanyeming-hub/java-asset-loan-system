package com.example.assetloan.dto;

import com.example.assetloan.domain.AssetStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * API のレスポンスボディ。エンティティを直接返さないのは、
 * 遅延ロードの罠（LazyInitializationException）と「DB 構造＝API 仕様」になるのを避けるため。
 */
public final class Responses {

    private Responses() {
    }

    public record DepartmentResponse(Long id, String code, String name) {
    }

    public record EmployeeResponse(Long id, String employeeNumber, String name, String email,
                                   Long departmentId, String departmentName, String role, boolean active) {
    }

    public record CategoryResponse(Long id, String name, int defaultLoanDays) {
    }

    /** 一覧用の軽い表現。 */
    public record AssetSummaryResponse(Long id, String managementNumber, String name, String categoryName,
                                       String manufacturer, String model, AssetStatus status,
                                       boolean lendable, LocalDate dueOn) {
    }

    /** 詳細用。現在の貸出情報（誰が・いつまで）を添える。 */
    public record AssetResponse(Long id, String managementNumber, String name,
                                Long categoryId, String categoryName, int defaultLoanDays,
                                String manufacturer, String model, AssetStatus status,
                                boolean lendable, LocalDate purchasedOn, String note,
                                CurrentLoanResponse currentLoan) {
    }

    public record CurrentLoanResponse(Long loanId, String requesterName, String requesterEmployeeNumber,
                                      LocalDate startedOn, LocalDate dueOn, boolean overdue) {
    }

    public record LoanResponse(Long id, String status, String statusLabel,
                               Long assetId, String managementNumber, String assetName, String categoryName,
                               Long requesterId, String requesterName, String requesterEmployeeNumber,
                               Long approverId, String approverName,
                               LocalDateTime requestedAt, LocalDate startedOn, LocalDate dueOn,
                               LocalDate returnedOn, boolean overdue,
                               String rejectReason, String returnNote) {
    }

    public record LoanHistoryResponse(Long id, String action, String status, String actorName,
                                      String note, LocalDateTime createdAt) {
    }

    /** ダッシュボード用の集計。 */
    public record AssetStatsResponse(long total, long available, long loaned, long maintenance, long retired,
                                     long requestedLoans, long overdueLoans) {
    }
}
