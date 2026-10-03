package com.example.assetloan.repository;

import com.example.assetloan.domain.LoanHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanHistoryRepository extends JpaRepository<LoanHistory, Long> {

    /** 1 件の貸出に対する操作履歴（古い順）。 */
    List<LoanHistory> findAllByLoanIdOrderByCreatedAtAsc(Long loanId);

    /** 備品単位の利用履歴。備品の利用状況を追うときに使う。 */
    List<LoanHistory> findAllByLoanAssetIdOrderByCreatedAtDesc(Long assetId);
}
