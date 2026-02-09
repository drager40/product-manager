package com.bugs.productmanager.service;

import com.bugs.productmanager.model.Budget;
import com.bugs.productmanager.repository.BudgetRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;

    public BudgetService(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    public List<Budget> findFiltered(String ym, String category, String division) {
        boolean hasYm = ym != null && !ym.isEmpty();
        boolean hasCat = category != null && !category.isEmpty();
        boolean hasDiv = division != null && !division.isEmpty();

        if (hasYm && hasCat && hasDiv) {
            Optional<Budget> b = budgetRepository.findByYmAndCategoryAndDivision(ym, category, division);
            return b.map(List::of).orElse(List.of());
        } else if (hasYm && hasCat) {
            return budgetRepository.findByYmAndCategory(ym, category);
        } else if (hasYm) {
            return budgetRepository.findByYm(ym);
        } else if (hasCat && hasDiv) {
            return budgetRepository.findByCategoryAndDivision(category, division);
        } else if (hasCat) {
            return budgetRepository.findByCategory(category);
        } else if (hasDiv) {
            return budgetRepository.findByDivision(division);
        } else {
            return budgetRepository.findAll();
        }
    }

    public BigDecimal calcMonthlyAmount(List<Budget> budgets) {
        BigDecimal total = BigDecimal.ZERO;
        for (Budget b : budgets) {
            total = total.add(b.getMonthlyAmount() != null ? b.getMonthlyAmount() : BigDecimal.ZERO);
        }
        return total;
    }

    public BigDecimal calcPrevRemaining(List<Budget> budgets) {
        BigDecimal total = BigDecimal.ZERO;
        for (Budget b : budgets) {
            total = total.add(b.getPrevRemaining() != null ? b.getPrevRemaining() : BigDecimal.ZERO);
        }
        return total;
    }

    public Budget findById(Long id) {
        return budgetRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid budget Id: " + id));
    }

    public Budget findByIdOrNull(Long id) {
        return budgetRepository.findById(id).orElse(null);
    }

    public Budget save(Budget budget) {
        return budgetRepository.save(budget);
    }

    /**
     * 신규 저장 시 동일 ym+category+division이 있으면 업데이트
     */
    public Budget saveOrUpdate(Budget budget) {
        if (budget.getId() == null) {
            Optional<Budget> existing = budgetRepository.findByYmAndCategoryAndDivision(
                    budget.getYm(), budget.getCategory(), budget.getDivision());
            if (existing.isPresent()) {
                Budget b = existing.get();
                b.setMonthlyAmount(budget.getMonthlyAmount());
                b.setPrevRemaining(budget.getPrevRemaining());
                return budgetRepository.save(b);
            }
        }
        return budgetRepository.save(budget);
    }

    public void deleteById(Long id) {
        budgetRepository.deleteById(id);
    }
}
