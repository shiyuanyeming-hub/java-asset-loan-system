package com.example.assetloan.repository;

import com.example.assetloan.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /** 社員番号で引く。API の「誰として操作しているか」の解決に使う。 */
    Optional<Employee> findByEmployeeNumber(String employeeNumber);

    Optional<Employee> findByEmail(String email);

    boolean existsByEmployeeNumber(String employeeNumber);

    List<Employee> findAllByActiveTrueOrderByEmployeeNumberAsc();

    List<Employee> findAllByDepartmentId(Long departmentId);
}
