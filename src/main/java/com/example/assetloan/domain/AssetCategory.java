package com.example.assetloan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 備品カテゴリ（PC・モニター・タブレット・プロジェクターなど）。
 *
 * <p>カテゴリごとに「標準の貸出日数」を持たせ、返却期限の自動計算に使う。
 */
@Entity
@Table(name = "asset_category")
public class AssetCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    /** 返却期限の既定日数（このカテゴリの備品を申請したときの貸出日数）。 */
    @Column(name = "default_loan_days", nullable = false)
    private int defaultLoanDays;

    protected AssetCategory() {
        // JPA 用
    }

    public AssetCategory(String name, int defaultLoanDays) {
        if (defaultLoanDays <= 0) {
            throw new IllegalArgumentException("defaultLoanDays must be positive");
        }
        this.name = name;
        this.defaultLoanDays = defaultLoanDays;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getDefaultLoanDays() {
        return defaultLoanDays;
    }

    public void changeDefaultLoanDays(int defaultLoanDays) {
        if (defaultLoanDays <= 0) {
            throw new IllegalArgumentException("defaultLoanDays must be positive");
        }
        this.defaultLoanDays = defaultLoanDays;
    }
}
