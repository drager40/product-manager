package com.bugs.productmanager.controller;

import com.bugs.productmanager.model.Budget;
import com.bugs.productmanager.model.Expense;
import com.bugs.productmanager.service.BudgetService;
import com.bugs.productmanager.service.ExpenseService;
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
    public String dashboard(Model model) {
        String currentYm = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String prevYm = YearMonth.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));

        // 이번 달
        List<Expense> currentExpenses = expenseService.findFiltered(List.of(currentYm), null, List.of(), null, null);
        BigDecimal currentUsed = expenseService.calcTotalAmount(currentExpenses);
        List<Budget> currentBudgets = budgetService.findFiltered(List.of(currentYm), null, List.of());
        BigDecimal currentBudgetTotal = budgetService.calcMonthlyAmount(currentBudgets).add(budgetService.calcPrevRemaining(currentBudgets));
        BigDecimal currentRemain = currentBudgetTotal.subtract(currentUsed);
        int currentUsage = currentBudgetTotal.compareTo(BigDecimal.ZERO) > 0
                ? currentUsed.multiply(BigDecimal.valueOf(100)).divide(currentBudgetTotal, 0, RoundingMode.HALF_UP).intValue() : 0;

        // 전월
        List<Expense> prevExpenses = expenseService.findFiltered(List.of(prevYm), null, List.of(), null, null);
        BigDecimal prevUsed = expenseService.calcTotalAmount(prevExpenses);

        // 전년 동월
        String prevYearYm = YearMonth.now().minusYears(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        List<Expense> prevYearExpenses = expenseService.findFiltered(List.of(prevYearYm), null, List.of(), null, null);
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

        return "dashboard";
    }
}
