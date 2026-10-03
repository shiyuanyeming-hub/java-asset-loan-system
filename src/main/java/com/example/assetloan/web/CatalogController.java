package com.example.assetloan.web;

import com.example.assetloan.domain.AssetCategory;
import com.example.assetloan.domain.Department;
import com.example.assetloan.domain.Employee;
import com.example.assetloan.dto.DtoMapper;
import com.example.assetloan.dto.Requests.CreateCategoryRequest;
import com.example.assetloan.dto.Requests.CreateDepartmentRequest;
import com.example.assetloan.dto.Requests.CreateEmployeeRequest;
import com.example.assetloan.dto.Responses.CategoryResponse;
import com.example.assetloan.dto.Responses.DepartmentResponse;
import com.example.assetloan.dto.Responses.EmployeeResponse;
import com.example.assetloan.exception.ForbiddenOperationException;
import com.example.assetloan.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * マスタ管理 API（部署・カテゴリ・社員）。
 *
 * <p>参照系は申請フォームのプルダウンで使うため全社員に開放し、
 * 登録系は管理者のみに制限している。
 */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/departments")
    public List<DepartmentResponse> departments() {
        return catalogService.departments().stream().map(DtoMapper::toResponse).toList();
    }

    @PostMapping("/departments")
    public ResponseEntity<DepartmentResponse> createDepartment(
            @Valid @RequestBody CreateDepartmentRequest request,
            @CurrentEmployee Employee actor) {
        requireAdmin(actor);
        Department created = catalogService.createDepartment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(DtoMapper.toResponse(created));
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return catalogService.categories().stream().map(DtoMapper::toResponse).toList();
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(
            @Valid @RequestBody CreateCategoryRequest request,
            @CurrentEmployee Employee actor) {
        requireAdmin(actor);
        AssetCategory created = catalogService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(DtoMapper.toResponse(created));
    }

    @GetMapping("/employees")
    public List<EmployeeResponse> employees(@CurrentEmployee Employee actor) {
        requireAdmin(actor);
        return catalogService.employees().stream().map(DtoMapper::toResponse).toList();
    }

    @PostMapping("/employees")
    public ResponseEntity<EmployeeResponse> createEmployee(
            @Valid @RequestBody CreateEmployeeRequest request,
            @CurrentEmployee Employee actor) {
        requireAdmin(actor);
        Employee created = catalogService.createEmployee(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(DtoMapper.toResponse(created));
    }

    private void requireAdmin(Employee actor) {
        if (!actor.isAdmin()) {
            throw new ForbiddenOperationException("管理者のみ実行できます（実行者: " + actor.getName() + "）");
        }
    }
}
