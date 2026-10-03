package com.example.assetloan.repository;

import com.example.assetloan.domain.Loan;
import com.example.assetloan.domain.LoanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    /**
     * 貸出を関連（備品・カテゴリ・申請者・承認者）ごと取得する。
     *
     * <p>レスポンス DTO は備品名やカテゴリ名・社員名を必ず使うので、
     * トランザクションの外で LazyInitializationException にならないよう先読みしておく。
     */
    @Override
    @EntityGraph(attributePaths = {"asset", "asset.category", "requester", "approver"})
    Optional<Loan> findById(Long id);

    /** 備品を占有している貸出（申請中・承認中）を取得する。重複申請の判定に使う。 */
    @Query("""
            select l from Loan l
            where l.asset.id = :assetId
              and l.status in :statuses
            """)
    @EntityGraph(attributePaths = {"asset", "asset.category", "requester", "approver"})
    List<Loan> findActiveByAssetId(@Param("assetId") Long assetId,
                                   @Param("statuses") Collection<LoanStatus> statuses);

    /** 社員の貸出一覧（新しい順）。 */
    @EntityGraph(attributePaths = {"asset", "asset.category", "requester", "approver"})
    Page<Loan> findAllByRequesterIdOrderByRequestedAtDesc(Long requesterId, Pageable pageable);

    /** 自分の貸出のうち、指定ステータスのもの。 */
    @EntityGraph(attributePaths = {"asset", "asset.category", "requester", "approver"})
    List<Loan> findAllByRequesterIdAndStatusOrderByRequestedAtDesc(Long requesterId, LoanStatus status);

    /** 管理者の承認待ち一覧（古い順に処理したいので昇順）。 */
    @EntityGraph(attributePaths = {"asset", "asset.category", "requester", "approver"})
    Page<Loan> findAllByStatusOrderByRequestedAtAsc(LoanStatus status, Pageable pageable);

    /**
     * 返却期限を過ぎても返却されていない貸出（延滞一覧）。
     */
    @Query("""
            select l from Loan l
            where l.status = com.example.assetloan.domain.LoanStatus.APPROVED
              and l.dueOn < :today
            order by l.dueOn asc
            """)
    @EntityGraph(attributePaths = {"asset", "asset.category", "requester", "approver"})
    List<Loan> findOverdue(@Param("today") LocalDate today);

    long countByAssetIdAndStatusIn(Long assetId, Collection<LoanStatus> statuses);
}
