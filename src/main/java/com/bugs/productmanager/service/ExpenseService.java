package com.bugs.productmanager.service;

import com.bugs.productmanager.model.Expense;
import com.bugs.productmanager.repository.ExpenseRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;

    public ExpenseService(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    public List<Expense> findFiltered(String ym, String category, String division) {
        boolean hasYm = ym != null && !ym.isEmpty();
        boolean hasCat = category != null && !category.isEmpty();
        boolean hasDiv = division != null && !division.isEmpty();

        if (hasYm && hasCat && hasDiv) {
            return expenseRepository.findByYmAndCategoryAndDivisionOrderByExpenseDateAsc(ym, category, division);
        } else if (hasYm && hasCat) {
            return expenseRepository.findByYmAndCategoryOrderByExpenseDateAsc(ym, category);
        } else if (hasYm && hasDiv) {
            return expenseRepository.findByYmAndDivisionOrderByExpenseDateAsc(ym, division);
        } else if (hasYm) {
            return expenseRepository.findByYmOrderByExpenseDateAsc(ym);
        } else if (hasCat && hasDiv) {
            return expenseRepository.findByCategoryAndDivisionOrderByExpenseDateAsc(category, division);
        } else if (hasCat) {
            return expenseRepository.findByCategoryOrderByExpenseDateAsc(category);
        } else if (hasDiv) {
            return expenseRepository.findByDivisionOrderByExpenseDateAsc(division);
        } else {
            return expenseRepository.findAll();
        }
    }

    public BigDecimal calcTotalAmount(List<Expense> expenses) {
        return expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Expense findById(Long id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid expense Id: " + id));
    }

    public Expense save(Expense expense) {
        return expenseRepository.save(expense);
    }

    public void deleteById(Long id) {
        expenseRepository.deleteById(id);
    }

    public Expense findByIdOrNull(Long id) {
        return expenseRepository.findById(id).orElse(null);
    }

    public List<String> findDistinctYm() {
        return expenseRepository.findDistinctYm();
    }

    public List<String> findDistinctCategory() {
        return expenseRepository.findDistinctCategory();
    }

    public List<String> findDistinctDivision() {
        return expenseRepository.findDistinctDivision();
    }

    /**
     * 예산별(ym+category+division) 사용금액 합계 맵
     */
    public Map<String, BigDecimal> calcUsedAmountByBudgetKey(List<Expense> expenses) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (Expense e : expenses) {
            String key = e.getYm() + "_" + e.getCategory() + "_" + e.getDivision();
            map.merge(key, e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO, BigDecimal::add);
        }
        return map;
    }

    /**
     * ym 필터가 없을 때 기본 ym 결정 (최신 월)
     */
    public String resolveDefaultYm(String ym, String category, String division) {
        boolean hasYm = ym != null && !ym.isEmpty();
        boolean hasCat = category != null && !category.isEmpty();
        boolean hasDiv = division != null && !division.isEmpty();

        if (!hasYm && !hasCat && !hasDiv) {
            List<String> ymList = findDistinctYm();
            if (!ymList.isEmpty()) {
                return ymList.get(0);
            }
        }
        return ym;
    }
}
