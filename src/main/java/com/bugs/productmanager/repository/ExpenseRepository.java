package com.bugs.productmanager.repository;

import com.bugs.productmanager.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByYmOrderByExpenseDateAsc(String ym);

    List<Expense> findByYmAndCategoryOrderByExpenseDateAsc(String ym, String category);

    List<Expense> findByYmAndCategoryAndDivisionOrderByExpenseDateAsc(String ym, String category, String division);

    List<Expense> findByYmAndDivisionOrderByExpenseDateAsc(String ym, String division);

    List<Expense> findByCategoryOrderByExpenseDateAsc(String category);

    List<Expense> findByCategoryAndDivisionOrderByExpenseDateAsc(String category, String division);

    List<Expense> findByDivisionOrderByExpenseDateAsc(String division);

    @Query("SELECT DISTINCT e.ym FROM Expense e ORDER BY e.ym DESC")
    List<String> findDistinctYm();

    @Query("SELECT DISTINCT e.category FROM Expense e ORDER BY e.category")
    List<String> findDistinctCategory();

    @Query("SELECT DISTINCT e.division FROM Expense e ORDER BY e.division")
    List<String> findDistinctDivision();
}
