package com.example.assetloan.dto;

import com.example.assetloan.domain.Asset;
import com.example.assetloan.domain.Department;
import com.example.assetloan.domain.Employee;
import com.example.assetloan.domain.Loan;
import com.example.assetloan.domain.LoanHistory;
import com.example.assetloan.dto.Responses.AssetResponse;
import com.example.assetloan.dto.Responses.AssetSummaryResponse;
import com.example.assetloan.dto.Responses.CategoryResponse;
import com.example.assetloan.dto.Responses.CurrentLoanResponse;
import com.example.assetloan.dto.Responses.DepartmentResponse;
import com.example.assetloan.dto.Responses.EmployeeResponse;
import com.example.assetloan.dto.Responses.LoanHistoryResponse;
import com.example.assetloan.dto.Responses.LoanResponse;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * エンティティ → レスポンス DTO の変換。
 *
 * <p>「どのフィールドを外に出すか」の判断を 1 か所に集約するために、
 * コントローラではなくこのクラスに置いている。
 */
public final class DtoMapper {

    /** 表示・期限判定に使うタイムゾーン（サーバが海外リージョンでも日本時間で扱う）。 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    private DtoMapper() {
    }

    public static DepartmentResponse toResponse(Department department) {
        return new DepartmentResponse(department.getId(), department.getCode(), department.getName());
    }

    public static EmployeeResponse toResponse(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getEmployeeNumber(),
                employee.getName(),
                employee.getEmail(),
                employee.getDepartment().getId(),
                employee.getDepartment().getName(),
                employee.getRole().name(),
                employee.isActive());
    }

    public static CategoryResponse toResponse(com.example.assetloan.domain.AssetCategory category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDefaultLoanDays());
    }

    public static AssetSummaryResponse toSummary(Asset asset, Loan activeLoan, LocalDate today) {
        return new AssetSummaryResponse(
                asset.getId(),
                asset.getManagementNumber(),
                asset.getName(),
                asset.getCategory().getName(),
                asset.getManufacturer(),
                asset.getModel(),
                asset.getStatus(),
                asset.isLendable(),
                activeLoan != null && activeLoan.getStatus() == com.example.assetloan.domain.LoanStatus.APPROVED
                        ? activeLoan.getDueOn() : null);
    }

    public static AssetResponse toResponse(Asset asset, Loan activeLoan, LocalDate today) {
        return new AssetResponse(
                asset.getId(),
                asset.getManagementNumber(),
                asset.getName(),
                asset.getCategory().getId(),
                asset.getCategory().getName(),
                asset.getCategory().getDefaultLoanDays(),
                asset.getManufacturer(),
                asset.getModel(),
                asset.getStatus(),
                asset.isLendable(),
                asset.getPurchasedOn(),
                asset.getNote(),
                toCurrentLoan(activeLoan, today));
    }

    private static CurrentLoanResponse toCurrentLoan(Loan loan, LocalDate today) {
        if (loan == null) {
            return null;
        }
        return new CurrentLoanResponse(
                loan.getId(),
                loan.getRequester().getName(),
                loan.getRequester().getEmployeeNumber(),
                loan.getStartedOn(),
                loan.getDueOn(),
                loan.isOverdue(today));
    }

    public static LoanResponse toResponse(Loan loan, LocalDate today) {
        return new LoanResponse(
                loan.getId(),
                loan.getStatus().name(),
                statusLabel(loan),
                loan.getAsset().getId(),
                loan.getAsset().getManagementNumber(),
                loan.getAsset().getName(),
                loan.getAsset().getCategory().getName(),
                loan.getRequester().getId(),
                loan.getRequester().getName(),
                loan.getRequester().getEmployeeNumber(),
                loan.getApprover() == null ? null : loan.getApprover().getId(),
                loan.getApprover() == null ? null : loan.getApprover().getName(),
                loan.getRequestedAt().atZone(ZONE).toLocalDateTime(),
                loan.getStartedOn(),
                loan.getDueOn(),
                loan.getReturnedOn(),
                loan.isOverdue(today),
                loan.getRejectReason(),
                loan.getReturnNote());
    }

    public static LoanHistoryResponse toResponse(LoanHistory history) {
        return new LoanHistoryResponse(
                history.getId(),
                history.getAction(),
                history.getStatus().name(),
                history.getActor().getName(),
                history.getNote(),
                history.getCreatedAt().atZone(ZONE).toLocalDateTime());
    }

    /** 画面表示用の日本語ラベル。 */
    public static String statusLabel(Loan loan) {
        return switch (loan.getStatus()) {
            case REQUESTED -> "申請中";
            case APPROVED -> "貸出中";
            case REJECTED -> "却下";
            case RETURNED -> "返却済み";
            case CANCELED -> "取消";
        };
    }
}
