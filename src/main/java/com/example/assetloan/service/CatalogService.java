package com.example.assetloan.service;

import com.example.assetloan.domain.AssetCategory;
import com.example.assetloan.domain.Department;
import com.example.assetloan.domain.Employee;
import com.example.assetloan.domain.Role;
import com.example.assetloan.dto.Requests.CreateCategoryRequest;
import com.example.assetloan.dto.Requests.CreateDepartmentRequest;
import com.example.assetloan.dto.Requests.CreateEmployeeRequest;
import com.example.assetloan.exception.DuplicateRequestException;
import com.example.assetloan.exception.ResourceNotFoundException;
import com.example.assetloan.repository.AssetCategoryRepository;
import com.example.assetloan.repository.EmployeeRepository;
import com.example.assetloan.repository.DepartmentRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * マスタ管理（部署・カテゴリ・社員）。
 *
 * <p>貸出とは違い、業務ルールは「コードや番号の重複を許さない」程度なので、
 * 1 クラスにまとめて見通しを優先している。
 */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final DepartmentRepository departmentRepository;
    private final AssetCategoryRepository categoryRepository;
    private final EmployeeRepository employeeRepository;

    public CatalogService(DepartmentRepository departmentRepository,
                          AssetCategoryRepository categoryRepository,
                          EmployeeRepository employeeRepository) {
        this.departmentRepository = departmentRepository;
        this.categoryRepository = categoryRepository;
        this.employeeRepository = employeeRepository;
    }

    public List<Department> departments() {
        return departmentRepository.findAll(Sort.by("code"));
    }

    @Transactional
    public Department createDepartment(CreateDepartmentRequest request) {
        String code = request.code().trim().toUpperCase();
        if (departmentRepository.existsByCode(code)) {
            throw new DuplicateRequestException("部署コードが重複しています: " + code);
        }
        return departmentRepository.save(new Department(code, request.name().trim()));
    }

    public List<AssetCategory> categories() {
        return categoryRepository.findAll(Sort.by("name"));
    }

    @Transactional
    public AssetCategory createCategory(CreateCategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByName(name)) {
            throw new DuplicateRequestException("カテゴリ名が重複しています: " + name);
        }
        return categoryRepository.save(new AssetCategory(name, request.defaultLoanDays()));
    }

    public List<Employee> employees() {
        return employeeRepository.findAll(Sort.by("employeeNumber"));
    }

    public Employee getEmployee(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("社員", id));
    }

    /**
     * 社員の登録。デモ用に「社員番号で自分を指定する」API からも使う。
     */
    @Transactional
    public Employee createEmployee(CreateEmployeeRequest request) {
        String number = request.employeeNumber().trim();
        if (employeeRepository.existsByEmployeeNumber(number)) {
            throw new DuplicateRequestException("社員番号が重複しています: " + number);
        }
        if (employeeRepository.findByEmail(request.email().trim()).isPresent()) {
            throw new DuplicateRequestException("メールアドレスが重複しています: " + request.email());
        }
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new ResourceNotFoundException("部署", request.departmentId()));
        Employee employee = new Employee(number, request.name().trim(), request.email().trim(),
                department, request.role());
        return employeeRepository.save(employee);
    }

    /**
     * 社員番号で社員を引く。未登録なら作成する（デモ用の簡易認証のため）。
     *
     * <p>デモを成立させるための割り切りとして、社員番号が {@code E9} で始まる場合は
     * 管理者として作成する（例: E9001）。権限チェック自体はサービス層で毎回行うので、
     * この規則は「初期データをどう用意するか」だけの話であり、認証の代わりにはならない。
     */
    @Transactional
    public Employee findOrCreateByEmployeeNumber(String employeeNumber) {
        return employeeRepository.findByEmployeeNumber(employeeNumber)
                .orElseGet(() -> {
                    Department department = departmentRepository.findByCode("GENERAL")
                            .orElseGet(() -> departmentRepository.save(new Department("GENERAL", "未所属")));
                    String email = employeeNumber.toLowerCase() + "@example.com";
                    Role role = employeeNumber.startsWith("E9") ? Role.ADMIN : Role.EMPLOYEE;
                    Employee employee = new Employee(employeeNumber, "社員 " + employeeNumber, email,
                            department, role);
                    return employeeRepository.save(employee);
                });
    }

    /** 社員番号で社員を引く（API の「誰として操作するか」の解決に使う）。 */
    public Employee getByEmployeeNumber(String employeeNumber) {
        return employeeRepository.findByEmployeeNumber(employeeNumber)
                .orElseThrow(() -> new ResourceNotFoundException("社員番号 " + employeeNumber + " の社員"));
    }
}
