package com.example.assetloan.repository;

import com.example.assetloan.domain.AssetCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetCategoryRepository extends JpaRepository<AssetCategory, Long> {

    Optional<AssetCategory> findByName(String name);

    boolean existsByName(String name);
}
