package com.bugs.productmanager.service;

import com.bugs.productmanager.model.Budget;
import com.bugs.productmanager.repository.BudgetRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;

    public BudgetService(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    public List<Budget> findFiltered(String ym, String category, String division) {
        List<String> divList = (division != null && !division.isEmpty()) ? List.of(division) : List.of();
        return findFiltered(ym != null && !ym.isEmpty() ? List.of(ym) : List.of(), category, divList);
    }

    public List<Budget> findFiltered(List<String> ymValues, String category, List<String> divValues) {
        Specification<Budget> spec = Specification.where(null);

        if (ymValues != null && !ymValues.isEmpty()) {
            spec = spec.and((r, q, cb) -> r.get("ym").in(ymValues));
        }
        boolean hasCat = category != null && !category.isEmpty();
        if (hasCat) spec = spec.and((r, q, cb) -> cb.equal(r.get("category"), category));
        if (divValues != null && !divValues.isEmpty()) {
            spec = spec.and((r, q, cb) -> r.get("division").in(divValues));
        }

        return budgetRepository.findAll(spec);
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

    /**
     * 월별 예산 합계 맵 (차트용): monthlyAmount + prevRemaining
     */
    public Map<String, BigDecimal> calcBudgetTotalByYm(List<Budget> budgets) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (Budget b : budgets) {
            BigDecimal total = (b.getMonthlyAmount() != null ? b.getMonthlyAmount() : BigDecimal.ZERO)
                    .add(b.getPrevRemaining() != null ? b.getPrevRemaining() : BigDecimal.ZERO);
            map.merge(b.getYm(), total, BigDecimal::add);
        }
        return map;
    }

    public void deleteById(Long id) {
        budgetRepository.deleteById(id);
    }
}
