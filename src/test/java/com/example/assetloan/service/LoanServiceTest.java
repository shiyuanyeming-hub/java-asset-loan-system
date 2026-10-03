package com.example.assetloan.service;

import com.example.assetloan.domain.Asset;
import com.example.assetloan.domain.AssetCategory;
import com.example.assetloan.domain.AssetStatus;
import com.example.assetloan.domain.Department;
import com.example.assetloan.domain.Employee;
import com.example.assetloan.domain.Loan;
import com.example.assetloan.domain.LoanHistory;
import com.example.assetloan.domain.LoanStatus;
import com.example.assetloan.domain.Role;
import com.example.assetloan.exception.DuplicateRequestException;
import com.example.assetloan.exception.ForbiddenOperationException;
import com.example.assetloan.exception.InvalidStateException;
import com.example.assetloan.repository.AssetRepository;
import com.example.assetloan.repository.EmployeeRepository;
import com.example.assetloan.repository.LoanHistoryRepository;
import com.example.assetloan.repository.LoanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 貸出サービスのテスト（リポジトリは Mockito で置き換え）。
 *
 * <p>「どの条件でどんな例外を返すか」＝ API のエラー仕様をここで固定する。
 * 備品のロックや DB 制約など、実際の DB 挙動は {@code LoanWorkflowIT}（結合テスト）で確認する。
 */
@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneId.of("Asia/Tokyo"));

    @Mock
    private LoanRepository loanRepository;
    @Mock
    private LoanHistoryRepository historyRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private EmployeeRepository employeeRepository;

    private LoanService loanService;

    private Asset asset;
    private Employee requester;
    private Employee admin;
    private Employee otherEmployee;

    @BeforeEach
    void setUp() {
        loanService = new LoanService(loanRepository, historyRepository, assetRepository,
                employeeRepository, FIXED_CLOCK, 14);

        AssetCategory category = new AssetCategory("ノートPC", 14);
        asset = new Asset("PC-0001", "ThinkPad X1", category, "Lenovo", "X1 Carbon", LocalDate.of(2024, 4, 1));
        setId(asset, 10L);

        Department department = new Department("SALES", "営業部");
        requester = new Employee("E1001", "佐藤 花子", "hanako@example.com", department, Role.EMPLOYEE);
        otherEmployee = new Employee("E1002", "鈴木 一郎", "ichiro@example.com", department, Role.EMPLOYEE);
        admin = new Employee("E9001", "管理者 太郎", "admin@example.com", department, Role.ADMIN);
        setId(requester, 1L);
        setId(otherEmployee, 2L);
        setId(admin, 9001L);
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

    private static void setId(Loan loan, Long id) {
        setId((Object) loan, id);
    }

    @Test
    @DisplayName("申請: 貸出可能な備品なら REQUESTED の貸出が作られ、履歴が残る")
    void requestLoanCreatesRequestedLoan() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(assetRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(asset));
        when(loanRepository.findActiveByAssetId(eq(10L), anyCollection())).thenReturn(List.of());
        when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> {
            Loan saved = invocation.getArgument(0);
            setId(saved, 100L);
            return saved;
        });

        Loan loan = loanService.requestLoan(10L, 1L);

        assertThat(loan.getStatus()).isEqualTo(LoanStatus.REQUESTED);
        assertThat(loan.getRequester()).isSameAs(requester);

        ArgumentCaptor<LoanHistory> history = ArgumentCaptor.forClass(LoanHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getAction()).isEqualTo("REQUEST");
        assertThat(history.getValue().getStatus()).isEqualTo(LoanStatus.REQUESTED);
    }

    @Test
    @DisplayName("申請: 貸出中の備品は INVALID_STATE（409）")
    void requestLoanRejectsLoanedAsset() {
        Asset loaned = new Asset("PC-0002", "MacBook Pro", new AssetCategory("ノートPC", 14),
                "Apple", "M4", null);
        loaned.markLoaned();
        setId(loaned, 11L);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(assetRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(loaned));

        assertThatThrownBy(() -> loanService.requestLoan(11L, 1L))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("貸し出せません");

        verify(loanRepository, never()).save(any(Loan.class));
    }

    @Test
    @DisplayName("申請: 既に申請中の貸出があると DUPLICATE_REQUEST（409）")
    void requestLoanRejectsDuplicate() {
        Loan existing = Loan.request(asset, otherEmployee, FIXED_CLOCK.instant());
        setId(existing, 99L);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(assetRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(asset));
        when(loanRepository.findActiveByAssetId(eq(10L), anyCollection())).thenReturn(List.of(existing));

        assertThatThrownBy(() -> loanService.requestLoan(10L, 1L))
                .isInstanceOf(DuplicateRequestException.class)
                .hasMessageContaining("鈴木 一郎");
    }

    @Test
    @DisplayName("承認: カテゴリの既定日数で返却期限が決まる")
    void approveUsesCategoryDefaultDays() {
        Loan loan = Loan.request(asset, requester, FIXED_CLOCK.instant());
        setId(loan, 100L);
        when(employeeRepository.findById(9001L)).thenReturn(Optional.of(admin));
        when(loanRepository.findById(100L)).thenReturn(Optional.of(loan));

        Loan approved = loanService.approve(100L, 9001L, null);

        assertThat(approved.getStatus()).isEqualTo(LoanStatus.APPROVED);
        assertThat(approved.getDueOn()).isEqualTo(LocalDate.of(2026, 10, 15)); // 10/01 + 14 日
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.LOANED);
    }

    @Test
    @DisplayName("承認: 日数を指定するとその日数が優先される")
    void approveWithExplicitDays() {
        Loan loan = Loan.request(asset, requester, FIXED_CLOCK.instant());
        setId(loan, 100L);
        when(employeeRepository.findById(9001L)).thenReturn(Optional.of(admin));
        when(loanRepository.findById(100L)).thenReturn(Optional.of(loan));

        Loan approved = loanService.approve(100L, 9001L, 3);

        assertThat(approved.getDueOn()).isEqualTo(LocalDate.of(2026, 10, 4));
    }

    @Test
    @DisplayName("承認: 一般社員が実行すると FORBIDDEN（403）")
    void approveByEmployeeIsForbidden() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(requester));

        assertThatThrownBy(() -> loanService.approve(100L, 1L, null))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("管理者のみ");

        verify(loanRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("却下: 理由が記録され、備品は貸出可能のまま")
    void rejectRecordsReason() {
        Loan loan = Loan.request(asset, requester, FIXED_CLOCK.instant());
        setId(loan, 100L);
        when(employeeRepository.findById(9001L)).thenReturn(Optional.of(admin));
        when(loanRepository.findById(100L)).thenReturn(Optional.of(loan));

        Loan rejected = loanService.reject(100L, 9001L, "別の社員が予約済み");

        assertThat(rejected.getStatus()).isEqualTo(LoanStatus.REJECTED);
        assertThat(rejected.getRejectReason()).isEqualTo("別の社員が予約済み");
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.AVAILABLE);
    }

    @Test
    @DisplayName("返却: 備品が貸出可能に戻り、再度申請できる状態になる")
    void returnMakesAssetAvailable() {
        Loan loan = Loan.request(asset, requester, FIXED_CLOCK.instant());
        setId(loan, 100L);
        loan.approve(admin, LocalDate.of(2026, 9, 20), 7); // 期限 2026-09-27（延滞中）
        when(employeeRepository.findById(9001L)).thenReturn(Optional.of(admin));
        when(loanRepository.findById(100L)).thenReturn(Optional.of(loan));

        Loan returned = loanService.returnAsset(100L, 9001L, "特に問題なし");

        assertThat(returned.getStatus()).isEqualTo(LoanStatus.RETURNED);
        assertThat(returned.getReturnedOn()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.AVAILABLE);
        assertThat(asset.isLendable()).isTrue();
    }

    @Test
    @DisplayName("返却: 返却済みをもう一度返却すると INVALID_STATE（409）")
    void doubleReturnIsRejected() {
        Loan loan = Loan.request(asset, requester, FIXED_CLOCK.instant());
        setId(loan, 100L);
        loan.approve(admin, LocalDate.of(2026, 9, 20), 7);
        loan.returnAsset(LocalDate.of(2026, 9, 25), null);
        when(employeeRepository.findById(9001L)).thenReturn(Optional.of(admin));
        when(loanRepository.findById(100L)).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> loanService.returnAsset(100L, 9001L, null))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("返却 できない状態です");
    }

    @Test
    @DisplayName("取消: 他人の申請は FORBIDDEN（403）")
    void cancelByOtherEmployeeIsForbidden() {
        Loan loan = Loan.request(asset, requester, FIXED_CLOCK.instant());
        setId(loan, 100L);
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(otherEmployee));
        when(loanRepository.findById(100L)).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> loanService.cancel(100L, 2L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("自分の申請のみ");
    }
}
