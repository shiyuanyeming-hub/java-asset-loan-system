package com.example.assetloan.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 貸出の状態遷移のテスト（Spring を起動しない純粋なユニットテスト）。
 *
 * <p>業務ルールのうち「状態遷移」はエンティティに閉じ込めてあるので、
 * DB もモックも無しで検証できる。
 */
class LoanTest {

    private Asset asset;
    private Employee requester;
    private Employee admin;

    @BeforeEach
    void setUp() {
        AssetCategory category = new AssetCategory("ノートPC", 14);
        asset = new Asset("PC-0001", "ThinkPad X1", category, "Lenovo", "X1 Carbon", LocalDate.of(2024, 4, 1));
        Department department = new Department("SALES", "営業部");
        requester = new Employee("E1001", "佐藤 花子", "hanako@example.com", department, Role.EMPLOYEE);
        admin = new Employee("E9001", "管理者 太郎", "admin@example.com", department, Role.ADMIN);
        setId(requester, 1L);
        setId(admin, 2L);
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("申請直後は REQUESTED で、備品は貸出可能のまま")
    void requestKeepsAssetAvailable() {
        Loan loan = Loan.request(asset, requester, Instant.parse("2026-10-01T00:00:00Z"));

        assertThat(loan.getStatus()).isEqualTo(LoanStatus.REQUESTED);
        assertThat(loan.getDueOn()).isNull();
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.AVAILABLE);
    }

    @Test
    @DisplayName("承認すると備品が LOANED になり、返却期限が設定される")
    void approveMarksAssetLoaned() {
        Loan loan = Loan.request(asset, requester, Instant.now());

        loan.approve(admin, LocalDate.of(2026, 10, 1), 14);

        assertThat(loan.getStatus()).isEqualTo(LoanStatus.APPROVED);
        assertThat(loan.getDueOn()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(loan.getApprover()).isSameAs(admin);
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.LOANED);
    }

    @Test
    @DisplayName("既に承認済みの申請を再承認すると IllegalStateException")
    void cannotApproveTwice() {
        Loan loan = Loan.request(asset, requester, Instant.now());
        loan.approve(admin, LocalDate.of(2026, 10, 1), 14);

        assertThatThrownBy(() -> loan.approve(admin, LocalDate.of(2026, 10, 2), 14))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("承認 できない状態です");
    }

    @Test
    @DisplayName("却下すると備品は貸出可能のまま、理由が残る")
    void rejectKeepsAssetAvailable() {
        Loan loan = Loan.request(asset, requester, Instant.now());

        loan.reject(admin, "同じ期間に他の社員が予約済み");

        assertThat(loan.getStatus()).isEqualTo(LoanStatus.REJECTED);
        assertThat(loan.getRejectReason()).isEqualTo("同じ期間に他の社員が予約済み");
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.AVAILABLE);
    }

    @Test
    @DisplayName("却下理由が空だと IllegalArgumentException")
    void rejectRequiresReason() {
        Loan loan = Loan.request(asset, requester, Instant.now());

        assertThatThrownBy(() -> loan.reject(admin, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("却下理由は必須");
    }

    @Test
    @DisplayName("返却すると備品が貸出可能に戻る（＝再貸出できる）")
    void returnAssetMakesAssetAvailableAgain() {
        Loan loan = Loan.request(asset, requester, Instant.now());
        loan.approve(admin, LocalDate.of(2026, 10, 1), 14);

        loan.returnAsset(LocalDate.of(2026, 10, 10), "キズなし");

        assertThat(loan.getStatus()).isEqualTo(LoanStatus.RETURNED);
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.AVAILABLE);
        assertThat(asset.isLendable()).isTrue();
    }

    @Test
    @DisplayName("申請中（未承認）のものは返却できない")
    void cannotReturnBeforeApproval() {
        Loan loan = Loan.request(asset, requester, Instant.now());

        assertThatThrownBy(() -> loan.returnAsset(LocalDate.of(2026, 10, 10), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("返却 できない状態です");
    }

    @Test
    @DisplayName("本人だけが取消できる")
    void onlyRequesterCanCancel() {
        Loan loan = Loan.request(asset, requester, Instant.now());

        assertThatThrownBy(() -> loan.cancel(admin))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("自分の申請のみ");

        loan.cancel(requester);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.CANCELED);
    }

    @Test
    @DisplayName("返却期限を過ぎた承認済みの貸出だけが延滞になる")
    void overdueOnlyForApprovedLoans() {
        Loan loan = Loan.request(asset, requester, Instant.now());
        loan.approve(admin, LocalDate.of(2026, 10, 1), 7); // 期限 2026-10-08

        assertThat(loan.isOverdue(LocalDate.of(2026, 10, 8))).isFalse();
        assertThat(loan.isOverdue(LocalDate.of(2026, 10, 9))).isTrue();

        loan.returnAsset(LocalDate.of(2026, 10, 20), null);
        assertThat(loan.isOverdue(LocalDate.of(2026, 10, 21))).isFalse();
    }

    @Test
    @DisplayName("貸出中の備品は状態変更できない（二重貸出を防ぐ）")
    void loanedAssetCannotChangeStatus() {
        asset.markLoaned();

        assertThatThrownBy(() -> asset.changeStatus(AssetStatus.MAINTENANCE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loaned asset cannot change status");
    }

    @Test
    @DisplayName("貸出可能でない備品を貸出中にしようとすると例外")
    void cannotLoanUnavailableAsset() {
        asset.changeStatus(AssetStatus.MAINTENANCE);

        assertThatThrownBy(asset::markLoaned)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not lendable");
    }
}
