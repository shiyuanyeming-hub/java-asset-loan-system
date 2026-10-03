package com.example.assetloan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 貸出申請 1 件。申請から返却までの状態をこのエンティティが持つ。
 *
 * <p>状態遷移のルールはこのクラスのメソッドに閉じ込めてあるので、
 * サービス層は「どのメソッドを呼ぶか」だけを判断すればよい
 * （不正な遷移は {@link IllegalStateException} になる）。
 */
@Entity
@Table(name = "loan")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    /** 申請した社員。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private Employee requester;

    /** 承認または却下した管理者。未処理のときは null。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private Employee approver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoanStatus status = LoanStatus.REQUESTED;

    /** 申請日時（監査用。JPA の Auditing を使わず業務上必要な箇所だけ明示的に持つ）。 */
    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    /** 貸出開始日（承認日）。 */
    @Column(name = "started_on")
    private LocalDate startedOn;

    /** 返却期限。承認時に「承認日 + カテゴリの既定日数」で決まる。 */
    @Column(name = "due_on")
    private LocalDate dueOn;

    /** 実際の返却日。 */
    @Column(name = "returned_on")
    private LocalDate returnedOn;

    @Column(name = "reject_reason", length = 300)
    private String rejectReason;

    @Column(name = "return_note", length = 300)
    private String returnNote;

    @Version
    private Long version;

    protected Loan() {
        // JPA 用
    }

    private Loan(Asset asset, Employee requester, Instant requestedAt) {
        this.asset = asset;
        this.requester = requester;
        this.requestedAt = requestedAt;
    }

    /** 貸出申請を作る（状態は REQUESTED）。 */
    public static Loan request(Asset asset, Employee requester, Instant requestedAt) {
        return new Loan(asset, requester, requestedAt);
    }

    /**
     * 承認する。貸出開始日と返却期限を確定し、備品を貸出中にする。
     *
     * @param approver          承認した管理者
     * @param startedOn         貸出開始日（通常は承認日）
     * @param loanDays          貸出日数（カテゴリの既定日数）
     */
    public void approve(Employee approver, LocalDate startedOn, int loanDays) {
        requireStatus(LoanStatus.REQUESTED, "承認");
        if (loanDays <= 0) {
            throw new IllegalArgumentException("loanDays must be positive");
        }
        this.approver = approver;
        this.startedOn = startedOn;
        this.dueOn = startedOn.plusDays(loanDays);
        this.asset.markLoaned();
        this.status = LoanStatus.APPROVED;
    }

    /** 却下する。備品の状態は変えない（貸出可能のまま）。 */
    public void reject(Employee approver, String reason) {
        requireStatus(LoanStatus.REQUESTED, "却下");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("却下理由は必須です");
        }
        this.approver = approver;
        this.rejectReason = reason;
        this.status = LoanStatus.REJECTED;
    }

    /** 返却処理。備品を貸出可能に戻す。 */
    public void returnAsset(LocalDate returnedOn, String note) {
        requireStatus(LoanStatus.APPROVED, "返却");
        this.returnedOn = returnedOn;
        this.returnNote = note;
        this.asset.markAvailable();
        this.status = LoanStatus.RETURNED;
    }

    /** 申請者による取消（承認前のみ）。 */
    public void cancel(Employee actor) {
        requireStatus(LoanStatus.REQUESTED, "取消");
        if (!actor.getId().equals(requester.getId())) {
            throw new IllegalStateException("自分の申請のみ取り消せます");
        }
        this.status = LoanStatus.CANCELED;
    }

    /** 返却期限を過ぎているか（返却済み・却下・取消は対象外）。 */
    public boolean isOverdue(LocalDate today) {
        return status == LoanStatus.APPROVED && dueOn != null && dueOn.isBefore(today);
    }

    private void requireStatus(LoanStatus expected, String operation) {
        if (status != expected) {
            throw new IllegalStateException(
                    "%s できない状態です（現在: %s / 必要: %s）".formatted(operation, status, expected));
        }
    }

    public Long getId() {
        return id;
    }

    public Asset getAsset() {
        return asset;
    }

    public Employee getRequester() {
        return requester;
    }

    public Employee getApprover() {
        return approver;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public LocalDate getStartedOn() {
        return startedOn;
    }

    public LocalDate getDueOn() {
        return dueOn;
    }

    public LocalDate getReturnedOn() {
        return returnedOn;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public String getReturnNote() {
        return returnNote;
    }
}
