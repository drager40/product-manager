package com.bugs.productmanager.controller;

import com.bugs.productmanager.config.CustomUserPrincipal;
import com.bugs.productmanager.model.Budget;
import com.bugs.productmanager.model.Expense;
import com.bugs.productmanager.service.BudgetService;
import com.bugs.productmanager.service.ExpenseService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
public class HomeController {

    private final ExpenseService expenseService;
    private final BudgetService budgetService;

    public HomeController(ExpenseService expenseService, BudgetService budgetService) {
        this.expenseService = expenseService;
        this.budgetService = budgetService;
    }

    @GetMapping("/")
    public String dashboard(Authentication auth, Model model) {
        String currentYm = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String prevYm = YearMonth.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));

        boolean admin = isAdmin(auth);
        String role = getUserRole(auth);
        String category = admin ? null : getUserCompany(auth);
        String department = ("ROLE_ADMIN".equals(role) || "ROLE_COMPANY".equals(role)) ? null : getUserDepartment(auth);
        String team = "ROLE_TEAM".equals(role) ? getUserTeam(auth) : null;

        // 이번 달
        List<Expense> currentExpenses = expenseService.findFilteredSingleTeam(List.of(currentYm), category, List.of(), null, null, department, team);
        BigDecimal currentUsed = expenseService.calcTotalAmount(currentExpenses);
        List<Budget> currentBudgets = budgetService.findFilteredSingleTeam(List.of(currentYm), category, List.of(), department, team);
        BigDecimal currentBudgetTotal = budgetService.calcMonthlyAmount(currentBudgets).add(budgetService.calcPrevRemaining(currentBudgets));
        BigDecimal currentRemain = currentBudgetTotal.subtract(currentUsed);
        int currentUsage = currentBudgetTotal.compareTo(BigDecimal.ZERO) > 0
                ? currentUsed.multiply(BigDecimal.valueOf(100)).divide(currentBudgetTotal, 0, RoundingMode.HALF_UP).intValue() : 0;

        // 전월
        List<Expense> prevExpenses = expenseService.findFilteredSingleTeam(List.of(prevYm), category, List.of(), null, null, department, team);
        BigDecimal prevUsed = expenseService.calcTotalAmount(prevExpenses);

        // 전년 동월
        String prevYearYm = YearMonth.now().minusYears(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        List<Expense> prevYearExpenses = expenseService.findFilteredSingleTeam(List.of(prevYearYm), category, List.of(), null, null, department, team);
        BigDecimal prevYearUsed = expenseService.calcTotalAmount(prevYearExpenses);

        // 카테고리별 이번달 사용금액
        Map<String, BigDecimal> catMap = new LinkedHashMap<>();
        for (Expense e : currentExpenses) {
            String cat = e.getCategory() != null ? e.getCategory() : "기타";
            catMap.merge(cat, e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO, BigDecimal::add);
        }

        // 최근 5건 경비
        List<Expense> recentExpenses = currentExpenses.stream()
                .sorted((a, b) -> b.getExpenseDate().compareTo(a.getExpenseDate()))
                .limit(5)
                .toList();

        model.addAttribute("currentYm", currentYm);
        model.addAttribute("currentUsed", currentUsed);
        model.addAttribute("currentBudgetTotal", currentBudgetTotal);
        model.addAttribute("currentRemain", currentRemain);
        model.addAttribute("currentUsage", currentUsage);
        model.addAttribute("currentCount", currentExpenses.size());
        model.addAttribute("prevUsed", prevUsed);
        model.addAttribute("prevYearUsed", prevYearUsed);
        model.addAttribute("catMap", catMap);
        model.addAttribute("recentExpenses", recentExpenses);
        model.addAttribute("isAdmin", admin);
        model.addAttribute("userRole", role);
        model.addAttribute("userCompany", getUserCompany(auth));
        model.addAttribute("userDepartment", getUserDepartment(auth));
        model.addAttribute("userTeam", getUserTeam(auth));

        return "dashboard";
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private String getUserRole(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority()).findFirst().orElse("ROLE_TEAM");
    }

    private String getUserCompany(Authentication auth) {
        if (auth.getPrincipal() instanceof CustomUserPrincipal p) return p.getCompany();
        return null;
    }

    private String getUserDepartment(Authentication auth) {
        if (auth.getPrincipal() instanceof CustomUserPrincipal p) return p.getDepartment();
        return null;
    }

    private String getUserTeam(Authentication auth) {
        if (auth.getPrincipal() instanceof CustomUserPrincipal p) return p.getTeam();
        return null;
    }
}
