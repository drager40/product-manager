package com.bugs.productmanager.repository;

import com.bugs.productmanager.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long>, JpaSpecificationExecutor<Budget> {

    Optional<Budget> findByYmAndCategoryAndDivision(String ym, String category, String division);

    Optional<Budget> findByYmAndCategoryAndDivisionAndDepartmentAndTeam(
            String ym, String category, String division, String department, String team);

    List<Budget> findByYm(String ym);

    List<Budget> findByYmAndCategory(String ym, String category);

    List<Budget> findByCategory(String category);

    List<Budget> findByCategoryAndDivision(String category, String division);

    List<Budget> findByDivision(String division);
}
