package com.bugs.productmanager.service;

import com.bugs.productmanager.model.Expense;
import com.bugs.productmanager.repository.ExpenseRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
        return findFiltered(ym, category, division, null, null);
    }

    public List<Expense> findFiltered(String ym, String category, String division, String purpose, String storeName) {
        Specification<Expense> spec = Specification.where(null);

        if (hasValue(ym))        spec = spec.and((r, q, cb) -> cb.equal(r.get("ym"), ym));
        if (hasValue(category))  spec = spec.and((r, q, cb) -> cb.equal(r.get("category"), category));
        if (hasValue(division))  spec = spec.and((r, q, cb) -> cb.equal(r.get("division"), division));
        if (hasValue(purpose))   spec = spec.and((r, q, cb) -> cb.like(r.get("purpose"), "%" + purpose + "%"));
        if (hasValue(storeName)) spec = spec.and((r, q, cb) -> cb.like(r.get("storeName"), "%" + storeName + "%"));

        return expenseRepository.findAll(spec, Sort.by("expenseDate").ascending());
    }

    private boolean hasValue(String s) {
        return s != null && !s.isEmpty();
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

    public List<String> findDistinctPurpose() {
        return expenseRepository.findDistinctPurpose();
    }

    public List<String> findDistinctStoreName() {
        return expenseRepository.findDistinctStoreName();
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
    public String resolveDefaultYm(String ym, String category, String division, String purpose, String storeName) {
        if (!hasValue(ym) && !hasValue(category) && !hasValue(division) && !hasValue(purpose) && !hasValue(storeName)) {
            List<String> ymList = findDistinctYm();
            if (!ymList.isEmpty()) {
                return ymList.get(0);
            }
        }
        return ym;
    }
}
