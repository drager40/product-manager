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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;;

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
            @RequestParam(required = false) List<String> ym,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> division,
            @RequestParam(required = false, defaultValue = "storeName") String searchType,
            @RequestParam(required = false) String searchKeyword,
            Authentication auth,
            Model model) {

        // searchType + searchKeyword → purpose / storeName 변환
        String purpose = null;
        String storeName = null;
        if (searchKeyword != null && !searchKeyword.trim().isEmpty()) {
            if ("storeName".equals(searchType)) {
                storeName = searchKeyword.trim();
            } else {
                purpose = searchKeyword.trim();
            }
        }

        // 빈 값 제거
        List<String> ymValues = ym != null ? ym.stream().filter(s -> s != null && !s.isEmpty()).toList() : List.of();
        List<String> divValues = division != null ? division.stream().filter(s -> s != null && !s.isEmpty()).toList() : List.of();

        List<String> ymList = expenseService.findDistinctYm();
        List<String> categoryList = expenseService.findDistinctCategory();
        List<String> divisionList = expenseService.findDistinctDivision();

        // 필터 없으면 최신 월로 기본 설정
        ymValues = expenseService.resolveDefaultYmList(ymValues, category, divValues, purpose, storeName);

        List<Expense> expenses = expenseService.findFiltered(ymValues, category, divValues, purpose, storeName);
        BigDecimal totalAmount = expenseService.calcTotalAmount(expenses);

        List<Budget> budgets = budgetService.findFiltered(ymValues, category, divValues);
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
        model.addAttribute("selectedYmList", ymValues);
        model.addAttribute("selectedCategory", category != null ? category : "");
        model.addAttribute("selectedDivisionList", divValues);
        model.addAttribute("selectedSearchType", searchType != null ? searchType : "storeName");
        model.addAttribute("selectedSearchKeyword", searchKeyword != null ? searchKeyword : "");
        model.addAttribute("isAdmin", isAdmin);
        // 사용률 계산
        int usagePercent = budgetTotal.compareTo(BigDecimal.ZERO) > 0
                ? totalAmount.multiply(BigDecimal.valueOf(100)).divide(budgetTotal, 0, java.math.RoundingMode.HALF_UP).intValue()
                : 0;
        model.addAttribute("usagePercent", usagePercent);
        model.addAttribute("budgetList", budgets);
        Map<String, BigDecimal> usedAmountMap = expenseService.calcUsedAmountByBudgetKey(expenses);
        model.addAttribute("usedAmountMap", usedAmountMap);

        // 예산별 사용률 맵 (Thymeleaf에서 복잡한 계산 제거)
        Map<String, Integer> budgetUsageMap = new LinkedHashMap<>();
        for (Budget b : budgets) {
            String key = b.getYm() + "_" + b.getCategory() + "_" + b.getDivision();
            BigDecimal used = usedAmountMap.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal total = b.getTotalBudget();
            int pct = total.compareTo(BigDecimal.ZERO) > 0
                    ? used.multiply(BigDecimal.valueOf(100)).divide(total, 0, java.math.RoundingMode.HALF_UP).intValue() : 0;
            budgetUsageMap.put(key, pct);
        }
        model.addAttribute("budgetUsageMap", budgetUsageMap);

        // 차트 데이터: 최근 1년치 고정 (현재월 기준 12개월 전 ~ 현재월)
        YearMonth now = YearMonth.now();
        YearMonth start = now.minusMonths(12);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        List<String> chartYmList = new ArrayList<>();
        for (YearMonth m = start; !m.isAfter(now); m = m.plusMonths(1)) {
            chartYmList.add(m.format(fmt));
        }
        List<Expense> chartExpenses = expenseService.findFiltered(chartYmList, category, divValues, purpose, storeName);
        Map<String, BigDecimal> chartUsedData = expenseService.calcAmountByYm(chartExpenses);
        List<Budget> chartBudgets = budgetService.findFiltered(chartYmList, category, divValues);
        Map<String, BigDecimal> chartBudgetData = budgetService.calcBudgetTotalByYm(chartBudgets);
        List<String> chartLabels = new ArrayList<>();
        List<BigDecimal> chartUsedValues = new ArrayList<>();
        List<BigDecimal> chartRemainValues = new ArrayList<>();
        // 전년 동월 데이터
        List<String> prevYearYmList = new ArrayList<>();
        for (String ym2 : chartYmList) {
            YearMonth m = YearMonth.parse(ym2, fmt);
            prevYearYmList.add(m.minusYears(1).format(fmt));
        }
        List<Expense> prevYearExpenses = expenseService.findFiltered(prevYearYmList, category, divValues, purpose, storeName);
        Map<String, BigDecimal> prevYearUsedData = expenseService.calcAmountByYm(prevYearExpenses);

        List<BigDecimal> chartPrevYearValues = new ArrayList<>();
        for (int i = 0; i < chartYmList.size(); i++) {
            String ym2 = chartYmList.get(i);
            chartLabels.add(ym2);
            BigDecimal used = chartUsedData.getOrDefault(ym2, BigDecimal.ZERO);
            BigDecimal budgetAmt = chartBudgetData.getOrDefault(ym2, BigDecimal.ZERO);
            chartUsedValues.add(used);
            chartRemainValues.add(budgetAmt.subtract(used));
            chartPrevYearValues.add(prevYearUsedData.getOrDefault(prevYearYmList.get(i), BigDecimal.ZERO));
        }
        model.addAttribute("chartLabels", chartLabels);
        model.addAttribute("chartUsedValues", chartUsedValues);
        model.addAttribute("chartRemainValues", chartRemainValues);
        model.addAttribute("chartPrevYearValues", chartPrevYearValues);

        // 파이 차트 데이터: 카테고리별 사용금액
        Map<String, BigDecimal> catAmountMap = new LinkedHashMap<>();
        for (Expense e : expenses) {
            String cat = e.getCategory() != null ? e.getCategory() : "기타";
            catAmountMap.merge(cat, e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO, BigDecimal::add);
        }
        model.addAttribute("pieLabels", new ArrayList<>(catAmountMap.keySet()));
        model.addAttribute("pieValues", new ArrayList<>(catAmountMap.values()));

        // 파이 차트: 구분별 사용금액
        Map<String, BigDecimal> divAmountMap = new LinkedHashMap<>();
        for (Expense e : expenses) {
            String div = e.getDivision() != null ? e.getDivision() : "기타";
            divAmountMap.merge(div, e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO, BigDecimal::add);
        }
        model.addAttribute("pieDivLabels", new ArrayList<>(divAmountMap.keySet()));
        model.addAttribute("pieDivValues", new ArrayList<>(divAmountMap.values()));

        // 상세조건(검색어) 사용 여부 → 차트에서 잔여금액 숨김
        boolean hasSearchKeyword = searchKeyword != null && !searchKeyword.trim().isEmpty();
        model.addAttribute("hasSearchKeyword", hasSearchKeyword);

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
    public String save(@ModelAttribute Expense expense,
                       @RequestParam(required = false) String returnFilter) {
        expenseService.save(expense);
        if (returnFilter != null && !returnFilter.isEmpty()) {
            return "redirect:/expenses?" + returnFilter;
        }
        return buildRedirect(expense.getYm(), expense.getCategory(), expense.getDivision());
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
                           @RequestParam(required = false) String returnFilter,
                           Model model) {
        model.addAttribute("expense", expenseService.findById(id));
        model.addAttribute("categoryList", expenseService.findDistinctCategory());
        model.addAttribute("divisionList", expenseService.findDistinctDivision());
        model.addAttribute("returnFilter", returnFilter != null ? returnFilter : "");
        return "expense/form";
    }

    @GetMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @RequestParam(required = false) String returnFilter) {
        expenseService.deleteById(id);
        if (returnFilter != null && !returnFilter.isEmpty()) {
            return "redirect:/expenses?" + returnFilter;
        }
        Expense expense = expenseService.findByIdOrNull(id);
        String ym = expense != null ? expense.getYm() : "";
        return buildRedirect(ym, null, null);
    }

    // ==================== Budget CRUD ====================

    @GetMapping("/budget/new")
    public String budgetForm(Model model) {
        model.addAttribute("budget", new Budget());
        return "expense/budget-form";
    }

    @GetMapping("/budget/{id}/edit")
    public String budgetEditForm(@PathVariable Long id,
                                 @RequestParam(required = false) String returnFilter,
                                 Model model) {
        model.addAttribute("budget", budgetService.findById(id));
        model.addAttribute("returnFilter", returnFilter != null ? returnFilter : "");
        return "expense/budget-form";
    }

    @PostMapping("/budget")
    public String saveBudget(@ModelAttribute Budget budget,
                             @RequestParam(required = false) String returnFilter) {
        Budget saved = budgetService.saveOrUpdate(budget);
        if (returnFilter != null && !returnFilter.isEmpty()) {
            return "redirect:/expenses?" + returnFilter;
        }
        return buildRedirect(saved.getYm(), saved.getCategory(), saved.getDivision());
    }

    @GetMapping("/budget/{id}/delete")
    public String deleteBudget(@PathVariable Long id,
                               @RequestParam(required = false) String returnFilter,
                               Authentication auth) {
        if (!isAdmin(auth)) return "redirect:/expenses";
        budgetService.deleteById(id);
        if (returnFilter != null && !returnFilter.isEmpty()) {
            return "redirect:/expenses?" + returnFilter;
        }
        return "redirect:/expenses";
    }

    // ==================== Excel Upload/Download ====================

    @GetMapping("/upload")
    public String uploadForm(Model model) {
        model.addAttribute("ym", "");
        return "expense/upload-form";
    }

    @PostMapping("/upload")
    public String uploadExcel(@RequestParam("file") MultipartFile file,
                              @RequestParam(value = "ym", required = false) String ym,
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
            @RequestParam(required = false) List<String> ym,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> division,
            @RequestParam(required = false, defaultValue = "storeName") String searchType,
            @RequestParam(required = false) String searchKeyword,
            HttpServletResponse response) throws IOException {

        String purpose = null;
        String storeName = null;
        if (searchKeyword != null && !searchKeyword.trim().isEmpty()) {
            if ("storeName".equals(searchType)) {
                storeName = searchKeyword.trim();
            } else {
                purpose = searchKeyword.trim();
            }
        }
        List<String> ymValues = ym != null ? ym.stream().filter(s -> s != null && !s.isEmpty()).toList() : List.of();
        List<String> divValues = division != null ? division.stream().filter(s -> s != null && !s.isEmpty()).toList() : List.of();
        List<Expense> expenses = expenseService.findFiltered(ymValues, category, divValues, purpose, storeName);
        List<Budget> budgets = budgetService.findFiltered(ymValues, category, divValues);

        boolean hasYm = !ymValues.isEmpty();
        boolean hasCat = category != null && !category.isEmpty();

        String filename = "경비예산";
        if (hasYm && ymValues.size() == 1) filename += "_" + ymValues.get(0).replace("-", "");
        else if (hasYm) filename += "_" + ymValues.size() + "개월";
        if (hasCat) filename += "_" + category;
        filename += ".xlsx";

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8));

        excelService.exportExcel(expenses, budgets, response.getOutputStream());
    }

    // ==================== 전체 백업 ====================

    @GetMapping("/backup")
    public void backupAll(HttpServletResponse response) throws IOException {
        List<Expense> allExpenses = expenseService.findFiltered(List.of(), null, List.of(), null, null);
        List<Budget> allBudgets = budgetService.findFiltered(List.of(), null, List.of());

        String filename = "경비예산_전체백업_" + java.time.LocalDate.now() + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8));

        excelService.exportExcel(allExpenses, allBudgets, response.getOutputStream());
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
