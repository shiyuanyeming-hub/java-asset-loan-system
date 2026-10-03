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

import java.time.Instant;

/**
 * 貸出の操作履歴（追記のみ）。
 *
 * <p>「今の状態」は {@link Loan#getStatus()} が持つが、監査のために
 * 「いつ・誰が・何をしたか」を別テーブルに残す。更新・削除はしない。
 */
@Entity
@Table(name = "loan_history")
public class LoanHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    /** 操作を行った社員。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private Employee actor;

    /** この操作の後に至った状態。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoanStatus status;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(length = 300)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LoanHistory() {
        // JPA 用
    }

    public LoanHistory(Loan loan, Employee actor, LoanStatus status, String action, String note, Instant createdAt) {
        this.loan = loan;
        this.actor = actor;
        this.status = status;
        this.action = action;
        this.note = note;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Loan getLoan() {
        return loan;
    }

    public Employee getActor() {
        return actor;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public String getAction() {
        return action;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
