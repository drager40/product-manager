package com.bugs.productmanager.repository;

import com.bugs.productmanager.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    Optional<Budget> findByYmAndCategoryAndDivision(String ym, String category, String division);

    List<Budget> findByYm(String ym);

    List<Budget> findByYmAndCategory(String ym, String category);

    List<Budget> findByCategory(String category);

    List<Budget> findByCategoryAndDivision(String category, String division);

    List<Budget> findByDivision(String division);
}
