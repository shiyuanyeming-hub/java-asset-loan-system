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

import java.time.LocalDate;

/**
 * 管理対象の備品 1 台 1 レコード（シリアル単位で管理する）。
 *
 * <p>型番単位ではなく個体単位にしているのは、「この 1 台だけ貸出中」を正確に判定するため。
 * {@link #status} は貸出サービスの承認・返却処理からのみ変更する。
 */
@Entity
@Table(name = "asset")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 管理番号（社内で一意。資産シールの番号を想定）。 */
    @Column(name = "management_number", nullable = false, unique = true, length = 30)
    private String managementNumber;

    @Column(nullable = false, length = 150)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private AssetCategory category;

    @Column(length = 100)
    private String manufacturer;

    @Column(length = 100)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetStatus status = AssetStatus.AVAILABLE;

    /** 購入日。古い備品を優先して貸し出すなどの判断材料。 */
    @Column(name = "purchased_on")
    private LocalDate purchasedOn;

    @Column(length = 500)
    private String note;

    /**
     * 楽観ロック用バージョン。同じ備品への同時申請を DB レベルで検出する保険
     * （通常はサービス層の悲観ロックで直列化する）。
     */
    @Version
    private Long version;

    protected Asset() {
        // JPA 用
    }

    public Asset(String managementNumber, String name, AssetCategory category,
                 String manufacturer, String model, LocalDate purchasedOn) {
        this.managementNumber = managementNumber;
        this.name = name;
        this.category = category;
        this.manufacturer = manufacturer;
        this.model = model;
        this.purchasedOn = purchasedOn;
    }

    /** 申請を受け付けてよい状態か。 */
    public boolean isLendable() {
        return status == AssetStatus.AVAILABLE;
    }

    /** 承認時に呼ぶ。貸出可能でなければ例外。 */
    public void markLoaned() {
        if (!isLendable()) {
            throw new IllegalStateException("asset " + managementNumber + " is not lendable: " + status);
        }
        this.status = AssetStatus.LOANED;
    }

    /** 返却時に呼ぶ。 */
    public void markAvailable() {
        if (status != AssetStatus.LOANED) {
            throw new IllegalStateException("asset " + managementNumber + " is not loaned: " + status);
        }
        this.status = AssetStatus.AVAILABLE;
    }

    /** 修理・点検・廃棄など、貸出以外の理由で状態を変える（貸出中は変更不可）。 */
    public void changeStatus(AssetStatus newStatus) {
        if (status == AssetStatus.LOANED) {
            throw new IllegalStateException("loaned asset cannot change status: " + managementNumber);
        }
        this.status = newStatus;
    }

    public void updateDetails(String name, AssetCategory category, String manufacturer,
                              String model, LocalDate purchasedOn, String note) {
        this.name = name;
        this.category = category;
        this.manufacturer = manufacturer;
        this.model = model;
        this.purchasedOn = purchasedOn;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public String getManagementNumber() {
        return managementNumber;
    }

    public String getName() {
        return name;
    }

    public AssetCategory getCategory() {
        return category;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getModel() {
        return model;
    }

    public AssetStatus getStatus() {
        return status;
    }

    public LocalDate getPurchasedOn() {
        return purchasedOn;
    }

    public String getNote() {
        return note;
    }
}
