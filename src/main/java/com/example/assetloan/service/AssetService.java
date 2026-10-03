package com.example.assetloan.service;

import com.example.assetloan.domain.Asset;
import com.example.assetloan.domain.AssetCategory;
import com.example.assetloan.domain.AssetStatus;
import com.example.assetloan.domain.Loan;
import com.example.assetloan.domain.LoanStatus;
import com.example.assetloan.dto.AssetSearchCondition;
import com.example.assetloan.dto.Requests.ChangeAssetStatusRequest;
import com.example.assetloan.dto.Requests.CreateAssetRequest;
import com.example.assetloan.dto.Requests.UpdateAssetRequest;
import com.example.assetloan.dto.Responses.AssetStatsResponse;
import com.example.assetloan.exception.DuplicateRequestException;
import com.example.assetloan.exception.InvalidStateException;
import com.example.assetloan.exception.ResourceNotFoundException;
import com.example.assetloan.repository.AssetCategoryRepository;
import com.example.assetloan.repository.AssetRepository;
import com.example.assetloan.repository.LoanRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 備品マスタの管理と検索。
 *
 * <p>貸出・返却にともなう状態変更は {@link LoanService} が担当し、
 * このクラスは「備品そのもの」の CRUD と検索条件の組み立てに責務を限定している。
 */
@Service
@Transactional(readOnly = true)
public class AssetService {

    /** 「備品を占有している」状態。重複申請の判定に使う。 */
    static final List<LoanStatus> HOLDING_STATUSES = List.of(LoanStatus.REQUESTED, LoanStatus.APPROVED);

    /** 終わった貸出（履歴として残る）。物理削除してよいかの判定に使う。 */
    static final List<LoanStatus> FINISHED_STATUSES =
            List.of(LoanStatus.RETURNED, LoanStatus.REJECTED, LoanStatus.CANCELED);

    private final AssetRepository assetRepository;
    private final AssetCategoryRepository categoryRepository;
    private final LoanRepository loanRepository;
    private final Clock clock;

    public AssetService(AssetRepository assetRepository,
                        AssetCategoryRepository categoryRepository,
                        LoanRepository loanRepository,
                        Clock clock) {
        this.assetRepository = assetRepository;
        this.categoryRepository = categoryRepository;
        this.loanRepository = loanRepository;
        this.clock = clock;
    }

    /**
     * 条件に一致する備品を検索する。
     *
     * <p>条件は Specification で組み立てるので、項目が増えても
     * メソッドが増えずに済む（Repository に findByNameAndStatusAnd... を増やさない）。
     */
    public Page<Asset> search(AssetSearchCondition condition, Pageable pageable) {
        return assetRepository.findAll(toSpecification(condition), pageable);
    }

    static Specification<Asset> toSpecification(AssetSearchCondition condition) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (condition != null && StringUtils.hasText(condition.keyword())) {
                String like = "%" + condition.keyword().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("managementNumber")), like),
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("manufacturer"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("model"), "")), like)));
            }
            if (condition != null && condition.category() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), condition.category()));
            }
            if (condition != null && condition.status() != null) {
                predicates.add(cb.equal(root.get("status"), condition.status()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public Asset getById(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("備品", id));
    }

    /** 備品に現在効いている貸出（申請中または貸出中）。無ければ空。 */
    public Optional<Loan> findActiveLoan(Long assetId) {
        return loanRepository.findActiveByAssetId(assetId, HOLDING_STATUSES).stream().findFirst();
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    @Transactional
    public Asset create(CreateAssetRequest request) {
        String managementNumber = request.managementNumber().trim();
        if (assetRepository.existsByManagementNumber(managementNumber)) {
            throw new DuplicateRequestException("管理番号が重複しています: " + managementNumber);
        }
        AssetCategory category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("備品カテゴリ", request.categoryId()));

        Asset asset = new Asset(managementNumber, request.name().trim(), category,
                request.manufacturer(), request.model(), request.purchasedOn());
        if (StringUtils.hasText(request.note())) {
            asset.updateDetails(asset.getName(), category, asset.getManufacturer(), asset.getModel(),
                    asset.getPurchasedOn(), request.note());
        }
        return assetRepository.save(asset);
    }

    @Transactional
    public Asset update(Long id, UpdateAssetRequest request) {
        Asset asset = getById(id);
        if (asset.getStatus() == AssetStatus.LOANED) {
            // 貸出中の備品の情報を書き換えると、貸出中の現物と DB の内容がずれる
            throw new InvalidStateException("貸出中の備品は編集できません。返却後に更新してください。");
        }
        AssetCategory category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("備品カテゴリ", request.categoryId()));
        asset.updateDetails(request.name().trim(), category, request.manufacturer(), request.model(),
                request.purchasedOn(), request.note());
        return asset;
    }

    /**
     * 備品の状態を変更する（修理・点検・廃棄など）。
     *
     * <p>貸出中・申請中の備品は変更できない。無効化したい場合は管理番号を消すのではなく
     * {@link AssetStatus#RETIRED} にする（履歴から参照できるようにするため）。
     */
    @Transactional
    public Asset changeStatus(Long id, ChangeAssetStatusRequest request) {
        Asset asset = getById(id);
        if (!HOLDING_STATUSES.isEmpty() && findActiveLoan(id).isPresent()) {
            throw new InvalidStateException(
                    "申請中または貸出中の備品は状態を変更できません。先に貸出を処理してください。");
        }
        asset.changeStatus(request.status());
        return asset;
    }

    @Transactional
    public void delete(Long id) {
        Asset asset = getById(id);
        if (findActiveLoan(id).isPresent()) {
            throw new InvalidStateException("申請中または貸出中の備品は削除できません。");
        }
        if (loanRepository.countByAssetIdAndStatusIn(id, FINISHED_STATUSES) > 0) {
            // 過去に貸し出した記録がある備品は物理削除せず、状態を RETIRED にする
            throw new InvalidStateException(
                    "貸出履歴のある備品は削除できません。状態を RETIRED（廃棄）にしてください。");
        }
        assetRepository.delete(asset);
    }

    public AssetStatsResponse stats() {
        long total = assetRepository.count();
        return new AssetStatsResponse(
                total,
                assetRepository.countByStatus(AssetStatus.AVAILABLE),
                assetRepository.countByStatus(AssetStatus.LOANED),
                assetRepository.countByStatus(AssetStatus.MAINTENANCE),
                assetRepository.countByStatus(AssetStatus.RETIRED),
                loanRepository.findAllByStatusOrderByRequestedAtAsc(LoanStatus.REQUESTED,
                        Pageable.ofSize(1)).getTotalElements(),
                loanRepository.findOverdue(today()).size());
    }
}
