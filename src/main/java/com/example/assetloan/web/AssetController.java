package com.example.assetloan.web;

import com.example.assetloan.domain.Asset;
import com.example.assetloan.domain.Loan;
import com.example.assetloan.dto.AssetSearchCondition;
import com.example.assetloan.dto.DtoMapper;
import com.example.assetloan.dto.PagedResponse;
import com.example.assetloan.dto.Requests;
import com.example.assetloan.dto.Responses.AssetResponse;
import com.example.assetloan.dto.Responses.AssetStatsResponse;
import com.example.assetloan.dto.Responses.AssetSummaryResponse;
import com.example.assetloan.service.AssetService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 備品の検索・参照（社員・管理者の両方が利用）と、備品マスタの管理（管理者のみ）。
 */
@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    /** 備品の検索。keyword は管理番号・名称・メーカー・型番の部分一致。 */
    @GetMapping
    public PagedResponse<AssetSummaryResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long category,
            @RequestParam(required = false) com.example.assetloan.domain.AssetStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        AssetSearchCondition condition = new AssetSearchCondition(keyword, category, status);
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("managementNumber"));
        Page<Asset> result = assetService.search(condition, pageable);
        LocalDate today = assetService.today();
        return PagedResponse.from(result,
                asset -> DtoMapper.toSummary(asset, assetService.findActiveLoan(asset.getId()).orElse(null), today));
    }

    @GetMapping("/{id}")
    public AssetResponse get(@PathVariable Long id) {
        Asset asset = assetService.getById(id);
        Optional<Loan> activeLoan = assetService.findActiveLoan(id);
        return DtoMapper.toResponse(asset, activeLoan.orElse(null), assetService.today());
    }

    @GetMapping("/stats")
    public AssetStatsResponse stats() {
        return assetService.stats();
    }

    @PostMapping
    public ResponseEntity<AssetResponse> create(@Valid @RequestBody Requests.CreateAssetRequest request,
                                                UriComponentsBuilder uriBuilder) {
        Asset created = assetService.create(request);
        URI location = uriBuilder.path("/api/assets/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location)
                .body(DtoMapper.toResponse(created, null, assetService.today()));
    }

    @PutMapping("/{id}")
    public AssetResponse update(@PathVariable Long id,
                                @Valid @RequestBody Requests.UpdateAssetRequest request) {
        Asset updated = assetService.update(id, request);
        return DtoMapper.toResponse(updated, assetService.findActiveLoan(id).orElse(null), assetService.today());
    }

    @PatchMapping("/{id}/status")
    public AssetResponse changeStatus(@PathVariable Long id,
                                      @Valid @RequestBody Requests.ChangeAssetStatusRequest request) {
        Asset updated = assetService.changeStatus(id, request);
        return DtoMapper.toResponse(updated, assetService.findActiveLoan(id).orElse(null), assetService.today());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        assetService.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
