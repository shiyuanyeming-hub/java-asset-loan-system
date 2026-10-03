package com.example.assetloan.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * API のリクエストボディ。<code>record</code> なので不変で、
 * Bean Validation のアノテーションをそのままフィールドに書ける。
 */
public final class Requests {

    private Requests() {
    }

    /** 備品の新規登録（管理者）。 */
    public record CreateAssetRequest(
            @NotBlank @Size(max = 30) String managementNumber,
            @NotBlank @Size(max = 150) String name,
            @NotNull Long categoryId,
            @Size(max = 100) String manufacturer,
            @Size(max = 100) String model,
            LocalDate purchasedOn,
            @Size(max = 500) String note
    ) {
    }

    /** 備品の更新（管理者）。管理番号は変更しない。 */
    public record UpdateAssetRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull Long categoryId,
            @Size(max = 100) String manufacturer,
            @Size(max = 100) String model,
            LocalDate purchasedOn,
            @Size(max = 500) String note
    ) {
    }

    /** 備品の状態変更（管理者）。貸出中は変更できない。 */
    public record ChangeAssetStatusRequest(
            @NotNull com.example.assetloan.domain.AssetStatus status
    ) {
    }

    /** カテゴリ登録（管理者）。 */
    public record CreateCategoryRequest(
            @NotBlank @Size(max = 50) String name,
            @Positive int defaultLoanDays
    ) {
    }

    /** 部署登録（管理者）。 */
    public record CreateDepartmentRequest(
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 100) String name
    ) {
    }

    /** 社員登録（管理者）。 */
    public record CreateEmployeeRequest(
            @NotBlank @Size(max = 20) String employeeNumber,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @NotNull Long departmentId,
            @NotNull com.example.assetloan.domain.Role role
    ) {
    }

    /** 貸出申請（社員）。 */
    public record CreateLoanRequest(
            @NotNull Long assetId
    ) {
    }

    /** 承認（管理者）。{@code loanDays} を省略するとカテゴリの既定日数を使う。 */
    public record ApproveLoanRequest(
            @Positive Integer loanDays
    ) {
    }

    /** 却下（管理者）。理由は必須。 */
    public record RejectLoanRequest(
            @NotBlank @Size(max = 300) String reason
    ) {
    }

    /** 返却（管理者）。 */
    public record ReturnLoanRequest(
            @Size(max = 300) String note
    ) {
    }
}
