package com.example.assetloan;

import com.example.assetloan.domain.Asset;
import com.example.assetloan.domain.AssetCategory;
import com.example.assetloan.domain.AssetStatus;
import com.example.assetloan.domain.Department;
import com.example.assetloan.domain.Employee;
import com.example.assetloan.domain.Loan;
import com.example.assetloan.domain.LoanStatus;
import com.example.assetloan.domain.Role;
import com.example.assetloan.dto.Requests.ChangeAssetStatusRequest;
import com.example.assetloan.exception.DuplicateRequestException;
import com.example.assetloan.exception.ForbiddenOperationException;
import com.example.assetloan.exception.InvalidStateException;
import com.example.assetloan.repository.AssetCategoryRepository;
import com.example.assetloan.repository.AssetRepository;
import com.example.assetloan.repository.DepartmentRepository;
import com.example.assetloan.repository.EmployeeRepository;
import com.example.assetloan.repository.LoanHistoryRepository;
import com.example.assetloan.repository.LoanRepository;
import com.example.assetloan.service.AssetService;
import com.example.assetloan.service.LoanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 実 DB（H2 / PostgreSQL 互換モード）を使った貸出ワークフローの結合テスト。
 *
 * <p>確認していること:
 * <ul>
 *   <li>申請 → 承認 → 返却が 1 本の流れとして動き、履歴が残る</li>
 *   <li>同じ備品への同時申請が 1 件しか通らない（悲観ロックの検証）</li>
 *   <li>権限違反と不正な状態遷移が想定どおりの例外になる</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class LoanWorkflowIT {

    @Autowired
    private LoanService loanService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private LoanRepository loanRepository;
    @Autowired
    private LoanHistoryRepository historyRepository;
    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private AssetCategoryRepository categoryRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Asset asset;
    private Employee requester;
    private Employee admin;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        loanRepository.deleteAll();
        assetRepository.deleteAll();
        employeeRepository.deleteAll();
        categoryRepository.deleteAll();
        departmentRepository.deleteAll();

        AssetCategory category = categoryRepository.save(new AssetCategory("ノートPC", 14));
        Department department = departmentRepository.save(new Department("SALES", "営業部"));
        requester = employeeRepository.save(
                new Employee("E1001", "佐藤 花子", "hanako@example.com", department, Role.EMPLOYEE));
        admin = employeeRepository.save(
                new Employee("E9001", "管理者 太郎", "admin@example.com", department, Role.ADMIN));
        asset = assetRepository.save(new Asset("PC-0001", "ThinkPad X1", category, "Lenovo", "X1 Carbon", null));
    }

    @Test
    @DisplayName("申請 → 承認 → 返却 の一連の流れが動作し、履歴が 3 件残る")
    void fullWorkflow() {
        Loan requested = loanService.requestLoan(asset.getId(), requester.getId());
        assertThat(requested.getStatus()).isEqualTo(LoanStatus.REQUESTED);
        // 申請段階では備品はまだ貸出中にならない
        assertThat(assetRepository.findById(asset.getId()).orElseThrow().getStatus())
                .isEqualTo(AssetStatus.AVAILABLE);

        Loan approved = loanService.approve(requested.getId(), admin.getId(), null);
        assertThat(approved.getStatus()).isEqualTo(LoanStatus.APPROVED);
        assertThat(approved.getDueOn()).isEqualTo(LocalDate.now().plusDays(14)); // カテゴリ既定 14 日
        assertThat(assetRepository.findById(asset.getId()).orElseThrow().getStatus())
                .isEqualTo(AssetStatus.LOANED);

        Loan returned = loanService.returnAsset(approved.getId(), admin.getId(), "返却確認済み");
        assertThat(returned.getStatus()).isEqualTo(LoanStatus.RETURNED);
        assertThat(assetRepository.findById(asset.getId()).orElseThrow().getStatus())
                .isEqualTo(AssetStatus.AVAILABLE);

        var history = historyRepository.findAllByLoanIdOrderByCreatedAtAsc(returned.getId());
        assertThat(history).hasSize(3);
        assertThat(history).extracting(h -> h.getAction())
                .containsExactly("REQUEST", "APPROVE", "RETURN");
        assertThat(history).extracting(h -> h.getStatus())
                .containsExactly(LoanStatus.REQUESTED, LoanStatus.APPROVED, LoanStatus.RETURNED);
    }

    @Test
    @DisplayName("返却後は同じ備品を再申請できる（在庫に戻る）")
    void assetCanBeReusedAfterReturn() {
        Loan first = loanService.requestLoan(asset.getId(), requester.getId());
        loanService.approve(first.getId(), admin.getId(), 7);
        loanService.returnAsset(first.getId(), admin.getId(), null);

        Loan second = loanService.requestLoan(asset.getId(), requester.getId());

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(second.getStatus()).isEqualTo(LoanStatus.REQUESTED);
    }

    @Test
    @DisplayName("貸出中の備品への申請は 409（INVALID_STATE）")
    void cannotRequestLoanedAsset() {
        Loan loan = loanService.requestLoan(asset.getId(), requester.getId());
        loanService.approve(loan.getId(), admin.getId(), 7);

        assertThatThrownBy(() -> loanService.requestLoan(asset.getId(), requester.getId()))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("貸し出せません");
    }

    @Test
    @DisplayName("同じ備品に同時に 10 件申請しても、成功するのは 1 件だけ")
    void concurrentRequestsOnlyOneSucceeds() throws Exception {
        int attempts = 10;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                tasks.add(() -> {
                    try {
                        loanService.requestLoan(asset.getId(), requester.getId());
                        return true;
                    } catch (DuplicateRequestException e) {
                        return false; // 期待どおり弾かれた
                    } catch (org.springframework.dao.PessimisticLockingFailureException e) {
                        // ロック待ちがタイムアウトした場合も「同時実行が直列化された」結果なので失敗扱いにする
                        return false;
                    }
                });
            }
            List<Future<Boolean>> futures = executor.invokeAll(tasks, 60, TimeUnit.SECONDS);
            long succeeded = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    succeeded++;
                }
            }

            assertThat(succeeded).isEqualTo(1);
            assertThat(loanRepository.findActiveByAssetId(asset.getId(),
                    List.of(LoanStatus.REQUESTED, LoanStatus.APPROVED))).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("一般社員は承認できない（403）")
    void employeeCannotApprove() {
        Loan loan = loanService.requestLoan(asset.getId(), requester.getId());

        assertThatThrownBy(() -> loanService.approve(loan.getId(), requester.getId(), null))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("管理者のみ");
    }

    @Test
    @DisplayName("延滞一覧には返却期限を過ぎた貸出だけが載る")
    void overdueListContainsOnlyExpiredLoans() {
        Loan loan = loanService.requestLoan(asset.getId(), requester.getId());
        loanService.approve(loan.getId(), admin.getId(), 7);

        // 承認直後はまだ延滞していない
        assertThat(loanService.findOverdue()).isEmpty();

        // 期限を 3 日前に書き換えて延滞状態を作る（時刻に依存しないテストにするため SQL で直接更新）
        jdbcTemplate.update("UPDATE loan SET due_on = ? WHERE id = ?",
                LocalDate.now().minusDays(3), loan.getId());

        List<Loan> overdue = loanService.findOverdue();
        assertThat(overdue).hasSize(1);
        assertThat(overdue.getFirst().getId()).isEqualTo(loan.getId());
    }

    @Test
    @DisplayName("貸出中の備品は状態変更できない（409）")
    void cannotChangeStatusWhileLoaned() {
        Loan loan = loanService.requestLoan(asset.getId(), requester.getId());
        loanService.approve(loan.getId(), admin.getId(), 7);

        assertThatThrownBy(() -> assetService.changeStatus(asset.getId(),
                new ChangeAssetStatusRequest(AssetStatus.MAINTENANCE)))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("状態を変更できません");
    }

    @Test
    @DisplayName("貸出履歴のある備品は削除できない（廃棄ステータスへ誘導）")
    void cannotDeleteAssetWithHistory() {
        Loan loan = loanService.requestLoan(asset.getId(), requester.getId());
        loanService.reject(loan.getId(), admin.getId(), "在庫確認のため一旦却下");

        assertThatThrownBy(() -> assetService.delete(asset.getId()))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("貸出履歴のある備品は削除できません");
    }
}
