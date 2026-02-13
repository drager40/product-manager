package com.bugs.productmanager.service;

import com.bugs.productmanager.model.Expense;
import com.bugs.productmanager.repository.ExpenseRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
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
        List<String> divList = (division != null && !division.isEmpty()) ? List.of(division) : List.of();
        List<String> emptyTeam = List.of();
        return findFiltered(ym != null && !ym.isEmpty() ? List.of(ym) : List.of(), category, divList, null, null, null, emptyTeam);
    }

    public List<Expense> findFiltered(List<String> ymValues, String category, List<String> divValues,
                                       String purpose, String storeName,
                                       String department, List<String> teamValues) {
        Specification<Expense> spec = Specification.where(null);

        if (ymValues != null && !ymValues.isEmpty()) {
            spec = spec.and((r, q, cb) -> r.get("ym").in(ymValues));
        }
        if (hasValue(category))  spec = spec.and((r, q, cb) -> cb.equal(r.get("category"), category));
        if (divValues != null && !divValues.isEmpty()) {
            spec = spec.and((r, q, cb) -> r.get("division").in(divValues));
        }
        if (hasValue(purpose))   spec = spec.and((r, q, cb) -> cb.like(r.get("purpose"), "%" + purpose + "%"));
        if (hasValue(storeName)) spec = spec.and((r, q, cb) -> cb.like(r.get("storeName"), "%" + storeName + "%"));
        if (hasValue(department)) spec = spec.and((r, q, cb) -> cb.equal(r.get("department"), department));
        // 팀 다중선택: __DEPT_ONLY__ = 실(자체), 팀명 = 해당 팀만
        if (teamValues != null && !teamValues.isEmpty()) {
            boolean hasDeptOnly = teamValues.contains("__DEPT_ONLY__");
            List<String> realTeams = teamValues.stream().filter(t -> !"__DEPT_ONLY__".equals(t) && hasValue(t)).toList();
            if (hasDeptOnly && !realTeams.isEmpty()) {
                // 실(자체) + 특정 팀들
                spec = spec.and((r, q, cb) -> cb.or(
                        cb.isNull(r.get("team")),
                        cb.equal(r.get("team"), ""),
                        r.get("team").in(realTeams)
                ));
            } else if (hasDeptOnly) {
                // 실(자체)만
                spec = spec.and((r, q, cb) -> cb.or(
                        cb.isNull(r.get("team")),
                        cb.equal(r.get("team"), "")
                ));
            } else if (!realTeams.isEmpty()) {
                // 특정 팀들만
                spec = spec.and((r, q, cb) -> r.get("team").in(realTeams));
            }
        }

        return expenseRepository.findAll(spec, Sort.by("expenseDate").ascending());
    }

    /** 하위호환: 단일 team 문자열 → List 변환 */
    public List<Expense> findFilteredSingleTeam(List<String> ymValues, String category, List<String> divValues,
                                       String purpose, String storeName,
                                       String department, String team) {
        List<String> teamValues = hasValue(team) ? List.of(team) : List.of();
        return findFiltered(ymValues, category, divValues, purpose, storeName, department, teamValues);
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

    @CacheEvict(value = {"distinctYm", "distinctCategory", "distinctDivision", "distinctPurpose", "distinctStoreName", "distinctDepartment", "distinctTeam"}, allEntries = true)
    public Expense save(Expense expense) {
        return expenseRepository.save(expense);
    }

    @CacheEvict(value = {"distinctYm", "distinctCategory", "distinctDivision", "distinctPurpose", "distinctStoreName", "distinctDepartment", "distinctTeam"}, allEntries = true)
    public void deleteById(Long id) {
        expenseRepository.deleteById(id);
    }

    public Expense findByIdOrNull(Long id) {
        return expenseRepository.findById(id).orElse(null);
    }

    @Cacheable("distinctYm")
    public List<String> findDistinctYm() {
        return expenseRepository.findDistinctYm();
    }

    @Cacheable("distinctCategory")
    public List<String> findDistinctCategory() {
        return expenseRepository.findDistinctCategory();
    }

    @Cacheable("distinctDivision")
    public List<String> findDistinctDivision() {
        return expenseRepository.findDistinctDivision();
    }

    @Cacheable("distinctPurpose")
    public List<String> findDistinctPurpose() {
        return expenseRepository.findDistinctPurpose();
    }

    @Cacheable("distinctStoreName")
    public List<String> findDistinctStoreName() {
        return expenseRepository.findDistinctStoreName();
    }

    @Cacheable("distinctDepartment")
    public List<String> findDistinctDepartment() {
        return expenseRepository.findDistinctDepartment();
    }

    @Cacheable("distinctTeam")
    public List<String> findDistinctTeam() {
        return expenseRepository.findDistinctTeam();
    }

    /**
     * 월별 사용금액 합계 맵 (차트용)
     */
    public Map<String, BigDecimal> calcAmountByYm(List<Expense> expenses) {
        Map<String, BigDecimal> map = new java.util.LinkedHashMap<>();
        for (Expense e : expenses) {
            String ym = e.getYm();
            map.merge(ym, e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO, BigDecimal::add);
        }
        return map;
    }

    /**
     * 예산별(ym+category+division+department+team) 사용금액 합계 맵
     */
    public Map<String, BigDecimal> calcUsedAmountByBudgetKey(List<Expense> expenses) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (Expense e : expenses) {
            String key = e.getYm() + "_" + e.getCategory() + "_" + e.getDivision()
                       + "_" + nullSafe(e.getDepartment()) + "_" + nullSafe(e.getTeam());
            map.merge(key, e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO, BigDecimal::add);
        }
        return map;
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    /**
     * ym 필터가 없을 때 기본 ym 결정 (최신 월)
     */
    public List<String> resolveDefaultYmList(List<String> ymValues, String category, List<String> divValues,
                                              String purpose, String storeName,
                                              String department, List<String> teamValues) {
        boolean hasYm = ymValues != null && !ymValues.isEmpty();
        boolean hasDiv = divValues != null && !divValues.isEmpty();
        boolean hasTeam = teamValues != null && !teamValues.isEmpty();
        if (!hasYm && !hasValue(category) && !hasDiv && !hasValue(purpose) && !hasValue(storeName)
                && !hasValue(department) && !hasTeam) {
            List<String> ymList = findDistinctYm();
            if (!ymList.isEmpty()) {
                return List.of(ymList.get(0));
            }
        }
        return ymValues != null ? ymValues : List.of();
    }
}
