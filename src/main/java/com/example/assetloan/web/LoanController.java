package com.example.assetloan.web;

import com.example.assetloan.config.WebConfig;
import com.example.assetloan.domain.Employee;
import com.example.assetloan.domain.Loan;
import com.example.assetloan.domain.LoanHistory;
import com.example.assetloan.dto.DtoMapper;
import com.example.assetloan.dto.PagedResponse;
import com.example.assetloan.dto.Requests;
import com.example.assetloan.dto.Responses.LoanHistoryResponse;
import com.example.assetloan.dto.Responses.LoanResponse;
import com.example.assetloan.service.AssetService;
import com.example.assetloan.service.LoanService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * 貸出の申請・承認・却下・返却。
 *
 * <p>操作者（{@link CurrentEmployee}）は {@code X-Employee-Number} ヘッダで指定する。
 * 承認・却下・返却はサービス層で管理者チェックを行う。
 */
@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService loanService;
    private final AssetService assetService;

    public LoanController(LoanService loanService, AssetService assetService) {
        this.loanService = loanService;
        this.assetService = assetService;
    }

    /** 貸出申請（社員）。貸出中の備品を指定すると 409 になる。 */
    @PostMapping
    public ResponseEntity<LoanResponse> request(@Valid @RequestBody Requests.CreateLoanRequest body,
                                                @CurrentEmployee Employee actor,
                                                UriComponentsBuilder uriBuilder) {
        Loan loan = loanService.requestLoan(body.assetId(), actor.getId());
        URI location = uriBuilder.path("/api/loans/{id}").buildAndExpand(loan.getId()).toUri();
        return ResponseEntity.created(location).body(toResponse(loan));
    }

    /** 自分の貸出一覧（新しい順）。 */
    @GetMapping("/my")
    public PagedResponse<LoanResponse> myLoans(@CurrentEmployee Employee actor,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<Loan> loans = loanService.findByRequester(actor.getId(), pageable);
        return PagedResponse.from(loans, this::toResponse);
    }

    /** 自分が今借りている備品（貸出中のみ）。 */
    @GetMapping("/my/active")
    public List<LoanResponse> myActiveLoans(@CurrentEmployee Employee actor) {
        return loanService.findByRequesterAndStatus(actor.getId(),
                        com.example.assetloan.domain.LoanStatus.APPROVED).stream()
                .map(this::toResponse)
                .toList();
    }

    /** 承認待ち一覧（管理者）。 */
    @GetMapping("/pending")
    public PagedResponse<LoanResponse> pending(@CurrentEmployee Employee actor,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        requireAdmin(actor);
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("requestedAt"));
        return PagedResponse.from(loanService.findPending(pageable), this::toResponse);
    }

    /** 延滞一覧（管理者）。返却期限を過ぎても返却されていない貸出。 */
    @GetMapping("/overdue")
    public List<LoanResponse> overdue(@CurrentEmployee Employee actor) {
        requireAdmin(actor);
        return loanService.findOverdue().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public LoanResponse get(@PathVariable Long id, @CurrentEmployee Employee actor) {
        Loan loan = loanService.getLoan(id);
        // 本人または管理者のみ参照可
        if (!loan.getRequester().getId().equals(actor.getId()) && !actor.isAdmin()) {
            throw new com.example.assetloan.exception.ForbiddenOperationException(
                    "他の社員の貸出は参照できません");
        }
        return toResponse(loan);
    }

    /** 貸出の操作履歴（申請→承認→返却のタイムライン）。 */
    @GetMapping("/{id}/history")
    public List<LoanHistoryResponse> history(@PathVariable Long id, @CurrentEmployee Employee actor) {
        Loan loan = loanService.getLoan(id);
        if (!loan.getRequester().getId().equals(actor.getId()) && !actor.isAdmin()) {
            throw new com.example.assetloan.exception.ForbiddenOperationException(
                    "他の社員の貸出履歴は参照できません");
        }
        List<LoanHistory> histories = loanService.history(id);
        return histories.stream().map(DtoMapper::toResponse).toList();
    }

    /** 承認（管理者）。 */
    @PostMapping("/{id}/approve")
    public LoanResponse approve(@PathVariable Long id,
                                @RequestBody(required = false) Requests.ApproveLoanRequest body,
                                @CurrentEmployee Employee actor) {
        Integer loanDays = body == null ? null : body.loanDays();
        return toResponse(loanService.approve(id, actor.getId(), loanDays));
    }

    /** 却下（管理者）。理由は必須。 */
    @PostMapping("/{id}/reject")
    public LoanResponse reject(@PathVariable Long id,
                               @Valid @RequestBody Requests.RejectLoanRequest body,
                               @CurrentEmployee Employee actor) {
        return toResponse(loanService.reject(id, actor.getId(), body.reason()));
    }

    /** 返却（管理者）。 */
    @PostMapping("/{id}/return")
    public LoanResponse returnAsset(@PathVariable Long id,
                                    @RequestBody(required = false) Requests.ReturnLoanRequest body,
                                    @CurrentEmployee Employee actor) {
        String note = body == null ? null : body.note();
        return toResponse(loanService.returnAsset(id, actor.getId(), note));
    }

    /** 申請の取消（本人・承認前のみ）。 */
    @PostMapping("/{id}/cancel")
    public LoanResponse cancel(@PathVariable Long id, @CurrentEmployee Employee actor) {
        return toResponse(loanService.cancel(id, actor.getId()));
    }

    private void requireAdmin(Employee actor) {
        if (!actor.isAdmin()) {
            throw new com.example.assetloan.exception.ForbiddenOperationException(
                    "管理者のみ参照できます");
        }
    }

    private LoanResponse toResponse(Loan loan) {
        LocalDate today = assetService.today();
        return DtoMapper.toResponse(loan, today);
    }
}
