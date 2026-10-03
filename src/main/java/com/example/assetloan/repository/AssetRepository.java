package com.example.assetloan.repository;

import com.example.assetloan.domain.Asset;
import com.example.assetloan.domain.AssetStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long>, JpaSpecificationExecutor<Asset> {

    /**
     * カテゴリを一緒に取得する（一覧・詳細のレスポンスで必ず名前を使うため）。
     *
     * <p>{@code spring.jpa.open-in-view=false} にしているので、
     * 画面に返す前に必要な関連を読み込んでおかないと LazyInitializationException になる。
     */
    @Override
    @EntityGraph(attributePaths = {"category"})
    Optional<Asset> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"category"})
    Page<Asset> findAll(Specification<Asset> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"category"})
    Optional<Asset> findByManagementNumber(String managementNumber);

    boolean existsByManagementNumber(String managementNumber);

    @EntityGraph(attributePaths = {"category"})
    List<Asset> findAllByCategoryId(Long categoryId);

    long countByStatus(AssetStatus status);

    /**
     * 行ロックを取って備品を取得する。
     *
     * <p>同じ備品に同時に申請が来たとき、片方だけが成功するようにするために使う
     * （SELECT ... FOR UPDATE）。H2/PostgreSQL のどちらでも動く。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"category"})
    @Query("select a from Asset a where a.id = :id")
    Optional<Asset> findByIdForUpdate(@Param("id") Long id);
}
