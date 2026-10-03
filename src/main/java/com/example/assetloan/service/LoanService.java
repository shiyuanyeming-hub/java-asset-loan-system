package com.example.assetloan.service;

import com.example.assetloan.config.ClockConfig;
import com.example.assetloan.domain.Asset;
import com.example.assetloan.domain.Employee;
import com.example.assetloan.domain.Loan;
import com.example.assetloan.domain.LoanHistory;
import com.example.assetloan.domain.LoanStatus;
import com.example.assetloan.exception.DuplicateRequestException;
import com.example.assetloan.exception.ForbiddenOperationException;
import com.example.assetloan.exception.InvalidStateException;
import com.example.assetloan.exception.ResourceNotFoundException;
import com.example.assetloan.repository.AssetRepository;
import com.example.assetloan.repository.EmployeeRepository;
import com.example.assetloan.repository.LoanHistoryRepository;
import com.example.assetloan.repository.LoanRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * 貸出の申請〜承認〜返却までのワークフロー。
 *
 * <p>このクラスが守っているルール:
 * <ul>
 *   <li>貸出可能な備品しか申請できない（貸出中・修理中・廃棄済みは 409）</li>
 *   <li>同じ備品に同時に申請が来ても 1 件しか通さない（備品行を SELECT ... FOR UPDATE でロック）</li>
 *   <li>承認・却下・返却は管理者のみ（一般社員が呼ぶと 403）</li>
 *   <li>許可されない状態遷移は 409（例: 返却済みの貸出を再度返却する）</li>
 *   <li>すべての操作を loan_history に追記する</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class LoanService {

    private final LoanRepository loanRepository;
    private final LoanHistoryRepository historyRepository;
    private final AssetRepository assetRepository;
    private final EmployeeRepository employeeRepository;
    private final Clock clock;
    private final int defaultLoanDays;

    public LoanService(LoanRepository loanRepository,
                       LoanHistoryRepository historyRepository,
                       AssetRepository assetRepository,
                       EmployeeRepository employeeRepository,
                       Clock clock,
                       @Value("${app.loan.default-loan-days:14}") int defaultLoanDays) {
        this.loanRepository = loanRepository;
        this.historyRepository = historyRepository;
        this.assetRepository = assetRepository;
        this.employeeRepository = employeeRepository;
        this.clock = clock;
        this.defaultLoanDays = defaultLoanDays;
    }

    /**
     * 貸出申請。備品の状態を確認してから申請レコードを作る。
     *
     * <p>備品の行をロックしてから確認するので、同時に複数の申請が来ても
     * 1 件目だけが成功し、2 件目は 409（DUPLICATE_REQUEST）になる。
     */
    @Transactional
    public Loan requestLoan(Long assetId, Long requesterId) {
        Employee requester = getEmployee(requesterId);
        requireActive(requester);
        Asset asset = assetRepository.findByIdForUpdate(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("備品", assetId));

        if (!asset.isLendable()) {
            throw new InvalidStateException(
                    "この備品は現在貸し出せません（状態: %s）".formatted(asset.getStatus()));
        }
        List<Loan> active = loanRepository.findActiveByAssetId(assetId, AssetService.HOLDING_STATUSES);
        if (!active.isEmpty()) {
            Loan existing = active.getFirst();
            throw new DuplicateRequestException(
                    "この備品には既に貸出申請があります（申請者: %s / 状態: %s）"
                            .formatted(existing.getRequester().getName(), existing.getStatus()));
        }

        Loan loan = Loan.request(asset, requester, Instant.now(clock));
        Loan saved = loanRepository.save(loan);
        record(saved, requester, "REQUEST", null);
        return saved;
    }

    /** 承認。貸出開始日と返却期限を確定し、備品を LOANED にする。 */
    @Transactional
    public Loan approve(Long loanId, Long approverId, Integer loanDays) {
        Employee approver = requireAdmin(approverId);
        Loan loan = getLoanForUpdate(loanId);
        LocalDate start = today();
        int days = resolveLoanDays(loan, loanDays);
        try {
            loan.approve(approver, start, days);
        } catch (IllegalStateException e) {
            throw new InvalidStateException(e.getMessage());
        }
        record(loan, approver, "APPROVE", "返却期限: %s".formatted(loan.getDueOn()));
        return loan;
    }

    /** 却下。理由は必須で、備品の状態は変えない。 */
    @Transactional
    public Loan reject(Long loanId, Long approverId, String reason) {
        Employee approver = requireAdmin(approverId);
        Loan loan = getLoanForUpdate(loanId);
        try {
            loan.reject(approver, reason);
        } catch (IllegalStateException e) {
            throw new InvalidStateException(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new InvalidStateException(e.getMessage());
        }
        record(loan, approver, "REJECT", reason);
        return loan;
    }

    /** 返却。備品を貸出可能に戻す。 */
    @Transactional
    public Loan returnAsset(Long loanId, Long actorId, String note) {
        Employee actor = requireAdmin(actorId);
        Loan loan = getLoanForUpdate(loanId);
        try {
            loan.returnAsset(today(), note);
        } catch (IllegalStateException e) {
            throw new InvalidStateException(e.getMessage());
        }
        record(loan, actor, "RETURN", StringUtils.hasText(note) ? note : null);
        return loan;
    }

    /** 申請の取消（申請者本人のみ・承認前のみ）。 */
    @Transactional
    public Loan cancel(Long loanId, Long actorId) {
        Employee actor = getEmployee(actorId);
        Loan loan = getLoanForUpdate(loanId);
        try {
            loan.cancel(actor);
        } catch (IllegalStateException e) {
            // 「自分の申請のみ」も「状態が違う」も同じ例外で返ってくるため、権限の問題は 403 に振り分ける
            if (e.getMessage() != null && e.getMessage().contains("自分の申請")) {
                throw new ForbiddenOperationException(e.getMessage());
            }
            throw new InvalidStateException(e.getMessage());
        }
        record(loan, actor, "CANCEL", null);
        return loan;
    }

    public Loan getLoan(Long loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("貸出", loanId));
    }

    public Page<Loan> findByRequester(Long requesterId, Pageable pageable) {
        return loanRepository.findAllByRequesterIdOrderByRequestedAtDesc(requesterId, pageable);
    }

    /** 自分の貸出のうち、指定ステータスのもの（例: 貸出中のみ）。 */
    public List<Loan> findByRequesterAndStatus(Long requesterId, LoanStatus status) {
        return loanRepository.findAllByRequesterIdAndStatusOrderByRequestedAtDesc(requesterId, status);
    }

    public Page<Loan> findPending(Pageable pageable) {
        return loanRepository.findAllByStatusOrderByRequestedAtAsc(LoanStatus.REQUESTED, pageable);
    }

    public List<Loan> findOverdue() {
        return loanRepository.findOverdue(today());
    }

    public List<LoanHistory> history(Long loanId) {
        getLoan(loanId); // 存在チェック
        return historyRepository.findAllByLoanIdOrderByCreatedAtAsc(loanId);
    }

    private int resolveLoanDays(Loan loan, Integer requestedDays) {
        if (requestedDays != null) {
            return requestedDays;
        }
        int categoryDays = loan.getAsset().getCategory().getDefaultLoanDays();
        return categoryDays > 0 ? categoryDays : defaultLoanDays;
    }

    private Employee getEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("社員", employeeId));
    }

    private void requireActive(Employee employee) {
        if (!employee.isActive()) {
            throw new ForbiddenOperationException("無効化された社員は操作できません: " + employee.getEmployeeNumber());
        }
    }

    private Employee requireAdmin(Long employeeId) {
        Employee employee = getEmployee(employeeId);
        requireActive(employee);
        if (!employee.isAdmin()) {
            throw new ForbiddenOperationException(
                    "管理者のみ実行できます（実行者: %s / 権限: %s）"
                            .formatted(employee.getName(), employee.getRole()));
        }
        return employee;
    }

    /**
     * 更新対象の貸出を取得する。
     *
     * <p>「承認」と「却下」を同時に押された場合などは、備品行の悲観ロック（{@code findByIdForUpdate}）と
     * {@code @Version}（楽観ロック）で二重処理を検出する。詳細は README の「技術的に工夫した点」を参照。
     */
    private Loan getLoanForUpdate(Long loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("貸出", loanId));
    }

    private void record(Loan loan, Employee actor, String action, String note) {
        historyRepository.save(new LoanHistory(loan, actor, loan.getStatus(), action, note, Instant.now(clock)));
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(ClockConfig.BUSINESS_ZONE));
    }
}
