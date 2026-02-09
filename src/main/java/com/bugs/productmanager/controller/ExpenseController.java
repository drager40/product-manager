package com.bugs.productmanager.controller;

import com.bugs.productmanager.model.Budget;
import com.bugs.productmanager.model.Expense;
import com.bugs.productmanager.service.BudgetService;
import com.bugs.productmanager.service.ExcelService;
import com.bugs.productmanager.service.ExpenseService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final BudgetService budgetService;
    private final ExcelService excelService;

    public ExpenseController(ExpenseService expenseService,
                             BudgetService budgetService,
                             ExcelService excelService) {
        this.expenseService = expenseService;
        this.budgetService = budgetService;
        this.excelService = excelService;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) String ym,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String division,
            @RequestParam(required = false) String purpose,
            @RequestParam(required = false) String storeName,
            Authentication auth,
            Model model) {

        List<String> ymList = expenseService.findDistinctYm();
        List<String> categoryList = expenseService.findDistinctCategory();
        List<String> divisionList = expenseService.findDistinctDivision();
        List<String> purposeList = expenseService.findDistinctPurpose();
        List<String> storeNameList = expenseService.findDistinctStoreName();

        // 필터 없으면 최신 월로 기본 설정
        ym = expenseService.resolveDefaultYm(ym, category, division, purpose, storeName);

        List<Expense> expenses = expenseService.findFiltered(ym, category, division, purpose, storeName);
        BigDecimal totalAmount = expenseService.calcTotalAmount(expenses);

        List<Budget> budgets = budgetService.findFiltered(ym, category, division);
        BigDecimal monthlyAmount = budgetService.calcMonthlyAmount(budgets);
        BigDecimal prevRemaining = budgetService.calcPrevRemaining(budgets);
        BigDecimal budgetTotal = monthlyAmount.add(prevRemaining);
        BigDecimal remaining = budgetTotal.subtract(totalAmount);

        boolean isAdmin = isAdmin(auth);

        model.addAttribute("expenses", expenses);
        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("totalCount", expenses.size());
        model.addAttribute("monthlyAmount", monthlyAmount);
        model.addAttribute("prevRemaining", prevRemaining);
        model.addAttribute("budgetTotal", budgetTotal);
        model.addAttribute("remaining", remaining);
        model.addAttribute("ymList", ymList);
        model.addAttribute("categoryList", categoryList);
        model.addAttribute("divisionList", divisionList);
        model.addAttribute("purposeList", purposeList);
        model.addAttribute("storeNameList", storeNameList);
        model.addAttribute("selectedYm", ym);
        model.addAttribute("selectedCategory", category != null ? category : "");
        model.addAttribute("selectedDivision", division != null ? division : "");
        model.addAttribute("selectedPurpose", purpose != null ? purpose : "");
        model.addAttribute("selectedStoreName", storeName != null ? storeName : "");
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("budgetList", budgets);
        model.addAttribute("usedAmountMap", expenseService.calcUsedAmountByBudgetKey(expenses));

        return "expense/list";
    }

    // ==================== Expense CRUD ====================

    @GetMapping("/new")
    public String createForm(Model model, Authentication auth) {
        if (!isAdmin(auth)) return "redirect:/expenses";
        model.addAttribute("expense", new Expense());
        model.addAttribute("categoryList", expenseService.findDistinctCategory());
        model.addAttribute("divisionList", expenseService.findDistinctDivision());
        return "expense/form";
    }

    @PostMapping
    public String save(@ModelAttribute Expense expense) {
        expenseService.save(expense);
        return buildRedirect(expense.getYm(), expense.getCategory(), expense.getDivision());
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("expense", expenseService.findById(id));
        model.addAttribute("categoryList", expenseService.findDistinctCategory());
        model.addAttribute("divisionList", expenseService.findDistinctDivision());
        return "expense/form";
    }

    @GetMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @RequestParam(required = false) String ym,
                         @RequestParam(required = false) String category,
                         @RequestParam(required = false) String division) {
        Expense expense = expenseService.findByIdOrNull(id);
        String redirectYm = (ym != null && !ym.isEmpty()) ? ym : (expense != null ? expense.getYm() : "");
        expenseService.deleteById(id);
        return buildRedirect(redirectYm, category, division);
    }

    // ==================== Budget CRUD ====================

    @GetMapping("/budget/new")
    public String budgetForm(Model model) {
        model.addAttribute("budget", new Budget());
        return "expense/budget-form";
    }

    @GetMapping("/budget/{id}/edit")
    public String budgetEditForm(@PathVariable Long id, Model model) {
        model.addAttribute("budget", budgetService.findById(id));
        return "expense/budget-form";
    }

    @PostMapping("/budget")
    public String saveBudget(@ModelAttribute Budget budget) {
        Budget saved = budgetService.saveOrUpdate(budget);
        return buildRedirect(saved.getYm(), saved.getCategory(), saved.getDivision());
    }

    @GetMapping("/budget/{id}/delete")
    public String deleteBudget(@PathVariable Long id,
                               @RequestParam(required = false) String ym,
                               @RequestParam(required = false) String category,
                               @RequestParam(required = false) String division,
                               Authentication auth) {
        if (!isAdmin(auth)) return "redirect:/expenses";
        Budget budget = budgetService.findByIdOrNull(id);
        String redirectYm = (ym != null && !ym.isEmpty()) ? ym : (budget != null ? budget.getYm() : "");
        budgetService.deleteById(id);
        return buildRedirect(redirectYm, category, division);
    }

    // ==================== Excel Upload/Download ====================

    @GetMapping("/upload")
    public String uploadForm(Model model) {
        model.addAttribute("ym", "");
        return "expense/upload-form";
    }

    @PostMapping("/upload")
    public String uploadExcel(@RequestParam("file") MultipartFile file,
                              @RequestParam("ym") String ym,
                              RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg", "파일을 선택해주세요.");
            return "redirect:/expenses/upload";
        }
        try {
            ExcelService.UploadResult result = excelService.importExcel(file, ym);
            redirectAttributes.addFlashAttribute("successMsg",
                    "업로드 완료! 예산 " + result.budgetCount() + "건, 경비 " + result.expenseCount() + "건 등록되었습니다.");
            redirectAttributes.addFlashAttribute("ym", ym);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "업로드 실패: " + e.getMessage());
            redirectAttributes.addFlashAttribute("ym", ym);
        }
        return "redirect:/expenses/upload";
    }

    @GetMapping("/download")
    public void downloadExcel(
            @RequestParam(required = false) String ym,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String division,
            @RequestParam(required = false) String purpose,
            @RequestParam(required = false) String storeName,
            HttpServletResponse response) throws IOException {

        List<Expense> expenses = expenseService.findFiltered(ym, category, division, purpose, storeName);
        List<Budget> budgets = budgetService.findFiltered(ym, category, division);

        boolean hasYm = ym != null && !ym.isEmpty();
        boolean hasCat = category != null && !category.isEmpty();

        String filename = "경비예산";
        if (hasYm) filename += "_" + ym.replace("-", "");
        if (hasCat) filename += "_" + category;
        filename += ".xlsx";

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8));

        excelService.exportExcel(expenses, budgets, response.getOutputStream());
    }

    // ==================== Helpers ====================

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private String buildRedirect(String ym, String category, String division) {
        return "redirect:/expenses?ym=" + enc(ym) + "&category=" + enc(category) + "&division=" + enc(division);
    }

    private String enc(String value) {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
