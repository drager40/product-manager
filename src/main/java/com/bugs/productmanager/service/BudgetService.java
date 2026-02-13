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
        List<String> emptyTeam = List.of();
        return findFiltered(ym != null && !ym.isEmpty() ? List.of(ym) : List.of(), category, divList, null, emptyTeam);
    }

    public List<Budget> findFilteredSingleTeam(List<String> ymValues, String category, List<String> divValues,
                                      String department, String team) {
        List<String> teamValues = (team != null && !team.isEmpty()) ? List.of(team) : List.of();
        return findFiltered(ymValues, category, divValues, department, teamValues);
    }

    public List<Budget> findFiltered(List<String> ymValues, String category, List<String> divValues,
                                      String department, List<String> teamValues) {
        Specification<Budget> spec = Specification.where(null);

        if (ymValues != null && !ymValues.isEmpty()) {
            spec = spec.and((r, q, cb) -> r.get("ym").in(ymValues));
        }
        boolean hasCat = category != null && !category.isEmpty();
        if (hasCat) spec = spec.and((r, q, cb) -> cb.equal(r.get("category"), category));
        if (divValues != null && !divValues.isEmpty()) {
            spec = spec.and((r, q, cb) -> r.get("division").in(divValues));
        }
        if (department != null && !department.isEmpty()) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("department"), department));
        }
        // 팀 다중선택: __DEPT_ONLY__ = 실(자체), 팀명 = 해당 팀만
        if (teamValues != null && !teamValues.isEmpty()) {
            boolean hasDeptOnly = teamValues.contains("__DEPT_ONLY__");
            List<String> realTeams = teamValues.stream()
                    .filter(t -> !"__DEPT_ONLY__".equals(t) && t != null && !t.isEmpty()).toList();
            if (hasDeptOnly && !realTeams.isEmpty()) {
                spec = spec.and((r, q, cb) -> cb.or(
                        cb.isNull(r.get("team")),
                        cb.equal(r.get("team"), ""),
                        r.get("team").in(realTeams)
                ));
            } else if (hasDeptOnly) {
                spec = spec.and((r, q, cb) -> cb.or(
                        cb.isNull(r.get("team")),
                        cb.equal(r.get("team"), "")
                ));
            } else if (!realTeams.isEmpty()) {
                spec = spec.and((r, q, cb) -> r.get("team").in(realTeams));
            }
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
     * 신규 저장 시 동일 ym+category+division+department+team이 있으면 업데이트
     */
    public Budget saveOrUpdate(Budget budget) {
        if (budget.getId() == null) {
            Optional<Budget> existing = budgetRepository.findByYmAndCategoryAndDivisionAndDepartmentAndTeam(
                    budget.getYm(), budget.getCategory(), budget.getDivision(),
                    budget.getDepartment(), budget.getTeam());
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
