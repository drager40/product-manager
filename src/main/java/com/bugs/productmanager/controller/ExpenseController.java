package com.bugs.productmanager.controller;

import com.bugs.productmanager.config.CustomUserPrincipal;
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
            @RequestParam(required = false) String department,
            @RequestParam(required = false) List<String> team,
            @RequestParam(required = false, defaultValue = "storeName") String searchType,
            @RequestParam(required = false) String searchKeyword,
            Authentication auth,
            Model model) {

        boolean admin = isAdmin(auth);

        // 권한 레벨에 따라 필터 강제 적용
        category = resolveCategory(auth, category);
        department = resolveDepartment(auth, department);
        List<String> teamValues = resolveTeamValues(auth, team);

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
        List<String> divisionList = expenseService.findDistinctDivision();

        // 권한별 필터 목록 제한
        String role = getUserRole(auth);
        String userComp = getUserCompany(auth);
        String userDept = getUserDepartment(auth);
        String userTm = getUserTeam(auth);

        List<String> categoryList;
        List<String> departmentList;
        List<String> teamList;

        if ("ROLE_ADMIN".equals(role)) {
            categoryList = expenseService.findDistinctCategory();
            departmentList = expenseService.findDistinctDepartment();
            teamList = expenseService.findDistinctTeam();
        } else if ("ROLE_COMPANY".equals(role)) {
            categoryList = userComp != null ? List.of(userComp) : List.of();
            departmentList = expenseService.findDistinctDepartment();  // 회사 내 실은 JS 캐스케이딩으로 필터
            teamList = expenseService.findDistinctTeam();
        } else if ("ROLE_DEPARTMENT".equals(role)) {
            categoryList = userComp != null ? List.of(userComp) : List.of();
            departmentList = userDept != null ? List.of(userDept) : List.of();
            teamList = expenseService.findDistinctTeam();  // 실 내 팀은 JS 캐스케이딩으로 필터
        } else {
            // ROLE_TEAM
            categoryList = userComp != null ? List.of(userComp) : List.of();
            departmentList = userDept != null ? List.of(userDept) : List.of();
            teamList = userTm != null ? List.of(userTm) : List.of();
        }

        // 필터 없으면 최신 월로 기본 설정
        ymValues = expenseService.resolveDefaultYmList(ymValues, category, divValues, purpose, storeName, department, teamValues);

        List<Expense> expenses = expenseService.findFiltered(ymValues, category, divValues, purpose, storeName, department, teamValues);
        BigDecimal totalAmount = expenseService.calcTotalAmount(expenses);

        List<Budget> budgets = budgetService.findFiltered(ymValues, category, divValues, department, teamValues);
        BigDecimal monthlyAmount = budgetService.calcMonthlyAmount(budgets);
        BigDecimal prevRemaining = budgetService.calcPrevRemaining(budgets);
        BigDecimal budgetTotal = monthlyAmount.add(prevRemaining);
        BigDecimal remaining = budgetTotal.subtract(totalAmount);

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
        model.addAttribute("departmentList", departmentList);
        model.addAttribute("teamList", teamList);
        model.addAttribute("selectedYmList", ymValues);
        model.addAttribute("selectedCategory", category != null ? category : "");
        model.addAttribute("selectedDivisionList", divValues);
        model.addAttribute("selectedDepartment", department != null ? department : "");
        model.addAttribute("selectedTeamList", teamValues);
        model.addAttribute("selectedSearchType", searchType != null ? searchType : "storeName");
        model.addAttribute("selectedSearchKeyword", searchKeyword != null ? searchKeyword : "");
        model.addAttribute("isAdmin", admin);
        model.addAttribute("userRole", getUserRole(auth));
        model.addAttribute("userCompany", getUserCompany(auth));
        model.addAttribute("userDepartment", getUserDepartment(auth));
        model.addAttribute("userTeam", getUserTeam(auth));

        // 사용률 계산
        int usagePercent = budgetTotal.compareTo(BigDecimal.ZERO) > 0
                ? totalAmount.multiply(BigDecimal.valueOf(100)).divide(budgetTotal, 0, java.math.RoundingMode.HALF_UP).intValue()
                : 0;
        model.addAttribute("usagePercent", usagePercent);
        model.addAttribute("budgetList", budgets);
        Map<String, BigDecimal> usedAmountMap = expenseService.calcUsedAmountByBudgetKey(expenses);
        model.addAttribute("usedAmountMap", usedAmountMap);

        // 예산별 사용률 맵
        Map<String, Integer> budgetUsageMap = new LinkedHashMap<>();
        for (Budget b : budgets) {
            String key = b.getYm() + "_" + b.getCategory() + "_" + b.getDivision()
                       + "_" + nullSafe(b.getDepartment()) + "_" + nullSafe(b.getTeam());
            BigDecimal used = usedAmountMap.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal total = b.getTotalBudget();
            int pct = total.compareTo(BigDecimal.ZERO) > 0
                    ? used.multiply(BigDecimal.valueOf(100)).divide(total, 0, java.math.RoundingMode.HALF_UP).intValue() : 0;
            budgetUsageMap.put(key, pct);
        }
        model.addAttribute("budgetUsageMap", budgetUsageMap);

        // 차트 데이터: 최근 1년치 고정
        YearMonth now = YearMonth.now();
        YearMonth start = now.minusMonths(12);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        List<String> chartYmList = new ArrayList<>();
        for (YearMonth m = start; !m.isAfter(now); m = m.plusMonths(1)) {
            chartYmList.add(m.format(fmt));
        }
        List<Expense> chartExpenses = expenseService.findFiltered(chartYmList, category, divValues, purpose, storeName, department, teamValues);
        Map<String, BigDecimal> chartUsedData = expenseService.calcAmountByYm(chartExpenses);
        List<Budget> chartBudgets = budgetService.findFiltered(chartYmList, category, divValues, department, teamValues);
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
        List<Expense> prevYearExpenses = expenseService.findFiltered(prevYearYmList, category, divValues, purpose, storeName, department, teamValues);
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

        // 상세조건(검색어) 사용 여부
        boolean hasSearchKeyword = searchKeyword != null && !searchKeyword.trim().isEmpty();
        model.addAttribute("hasSearchKeyword", hasSearchKeyword);

        return "expense/list";
    }

    // ==================== Expense CRUD ====================

    @GetMapping("/new")
    public String createForm(Model model, Authentication auth) {
        Expense expense = new Expense();
        // 권한별 자동 설정
        if (!isAdmin(auth)) {
            String comp = getUserCompany(auth);
            if (comp != null) expense.setCategory(comp);
            expense.setDepartment(getUserDepartment(auth));
            expense.setTeam(getUserTeam(auth));
        }
        model.addAttribute("expense", expense);
        model.addAttribute("categoryList", expenseService.findDistinctCategory());
        model.addAttribute("divisionList", expenseService.findDistinctDivision());
        model.addAttribute("isAdmin", isAdmin(auth));
        model.addAttribute("userRole", getUserRole(auth));
        model.addAttribute("userCompany", getUserCompany(auth));
        model.addAttribute("userDepartment", getUserDepartment(auth));
        model.addAttribute("userTeam", getUserTeam(auth));
        return "expense/form";
    }

    @PostMapping
    public String save(@ModelAttribute Expense expense,
                       @RequestParam(required = false) String returnFilter,
                       Authentication auth) {
        // 권한별 강제 설정
        if (!isAdmin(auth)) {
            String comp = getUserCompany(auth);
            if (comp != null) expense.setCategory(comp);
            String role = getUserRole(auth);
            if ("ROLE_DEPARTMENT".equals(role) || "ROLE_TEAM".equals(role)) {
                expense.setDepartment(getUserDepartment(auth));
            }
            if ("ROLE_TEAM".equals(role)) {
                expense.setTeam(getUserTeam(auth));
            }
        }
        expenseService.save(expense);
        if (returnFilter != null && !returnFilter.isEmpty()) {
            return "redirect:/expenses?" + returnFilter;
        }
        return buildRedirect(expense.getYm(), expense.getCategory(), expense.getDivision(), expense.getDepartment(), expense.getTeam());
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
                           @RequestParam(required = false) String returnFilter,
                           Authentication auth,
                           Model model) {
        Expense expense = expenseService.findById(id);
        // USER인 경우 자기 팀 경비만 수정 가능
        if (!isAdmin(auth) && !isOwnTeamExpense(auth, expense)) {
            return "redirect:/expenses";
        }
        model.addAttribute("expense", expense);
        model.addAttribute("categoryList", expenseService.findDistinctCategory());
        model.addAttribute("divisionList", expenseService.findDistinctDivision());
        model.addAttribute("returnFilter", returnFilter != null ? returnFilter : "");
        model.addAttribute("isAdmin", isAdmin(auth));
        model.addAttribute("userRole", getUserRole(auth));
        model.addAttribute("userCompany", getUserCompany(auth));
        model.addAttribute("userDepartment", getUserDepartment(auth));
        model.addAttribute("userTeam", getUserTeam(auth));
        return "expense/form";
    }

    @GetMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @RequestParam(required = false) String returnFilter,
                         Authentication auth) {
        // USER인 경우 자기 팀 경비만 삭제 가능
        Expense expense = expenseService.findByIdOrNull(id);
        if (!isAdmin(auth) && (expense == null || !isOwnTeamExpense(auth, expense))) {
            return "redirect:/expenses";
        }
        expenseService.deleteById(id);
        if (returnFilter != null && !returnFilter.isEmpty()) {
            return "redirect:/expenses?" + returnFilter;
        }
        String ym = expense != null ? expense.getYm() : "";
        return buildRedirect(ym, null, null, null, null);
    }

    // ==================== Budget CRUD ====================

    @GetMapping("/budget/new")
    public String budgetForm(Model model, Authentication auth) {
        Budget budget = new Budget();
        if (!isAdmin(auth)) {
            String comp = getUserCompany(auth);
            if (comp != null) budget.setCategory(comp);
            budget.setDepartment(getUserDepartment(auth));
            budget.setTeam(getUserTeam(auth));
        }
        model.addAttribute("budget", budget);
        model.addAttribute("isAdmin", isAdmin(auth));
        model.addAttribute("userRole", getUserRole(auth));
        model.addAttribute("userCompany", getUserCompany(auth));
        model.addAttribute("userDepartment", getUserDepartment(auth));
        model.addAttribute("userTeam", getUserTeam(auth));
        return "expense/budget-form";
    }

    @GetMapping("/budget/{id}/edit")
    public String budgetEditForm(@PathVariable Long id,
                                 @RequestParam(required = false) String returnFilter,
                                 Authentication auth,
                                 Model model) {
        Budget budget = budgetService.findById(id);
        if (!isAdmin(auth) && !isOwnTeamBudget(auth, budget)) {
            return "redirect:/expenses";
        }
        model.addAttribute("budget", budget);
        model.addAttribute("returnFilter", returnFilter != null ? returnFilter : "");
        model.addAttribute("isAdmin", isAdmin(auth));
        model.addAttribute("userRole", getUserRole(auth));
        model.addAttribute("userCompany", getUserCompany(auth));
        model.addAttribute("userDepartment", getUserDepartment(auth));
        model.addAttribute("userTeam", getUserTeam(auth));
        return "expense/budget-form";
    }

    @PostMapping("/budget")
    public String saveBudget(@ModelAttribute Budget budget,
                             @RequestParam(required = false) String returnFilter,
                             Authentication auth) {
        if (!isAdmin(auth)) {
            String comp = getUserCompany(auth);
            if (comp != null) budget.setCategory(comp);
            String role = getUserRole(auth);
            if ("ROLE_DEPARTMENT".equals(role) || "ROLE_TEAM".equals(role)) {
                budget.setDepartment(getUserDepartment(auth));
            }
            if ("ROLE_TEAM".equals(role)) {
                budget.setTeam(getUserTeam(auth));
            }
        }
        Budget saved = budgetService.saveOrUpdate(budget);
        if (returnFilter != null && !returnFilter.isEmpty()) {
            return "redirect:/expenses?" + returnFilter;
        }
        return buildRedirect(saved.getYm(), saved.getCategory(), saved.getDivision(), saved.getDepartment(), saved.getTeam());
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
    public String uploadForm(Model model, Authentication auth) {
        model.addAttribute("ym", "");
        model.addAttribute("isAdmin", isAdmin(auth));
        model.addAttribute("userRole", getUserRole(auth));
        model.addAttribute("userCompany", getUserCompany(auth));
        model.addAttribute("userDepartment", getUserDepartment(auth));
        model.addAttribute("userTeam", getUserTeam(auth));
        return "expense/upload-form";
    }

    @PostMapping("/upload")
    public String uploadExcel(@RequestParam("file") MultipartFile file,
                              @RequestParam(value = "ym", required = false) String ym,
                              @RequestParam(value = "department", required = false) String department,
                              @RequestParam(value = "team", required = false) String team,
                              Authentication auth,
                              RedirectAttributes redirectAttributes) {
        department = resolveDepartment(auth, department);
        // 업로드는 단일 팀이므로 ROLE_TEAM인 경우 강제
        String role = getUserRole(auth);
        if ("ROLE_TEAM".equals(role)) team = getUserTeam(auth);
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg", "파일을 선택해주세요.");
            return "redirect:/expenses/upload";
        }
        try {
            ExcelService.UploadResult result = excelService.importExcel(file, ym, department, team);
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
            @RequestParam(required = false) String department,
            @RequestParam(required = false) List<String> team,
            @RequestParam(required = false, defaultValue = "storeName") String searchType,
            @RequestParam(required = false) String searchKeyword,
            Authentication auth,
            HttpServletResponse response) throws IOException {

        category = resolveCategory(auth, category);
        department = resolveDepartment(auth, department);
        List<String> teamValues = resolveTeamValues(auth, team);

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
        List<Expense> expenses = expenseService.findFiltered(ymValues, category, divValues, purpose, storeName, department, teamValues);
        List<Budget> budgets = budgetService.findFiltered(ymValues, category, divValues, department, teamValues);

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
    public void backupAll(Authentication auth, HttpServletResponse response) throws IOException {
        String cat = resolveCategory(auth, null);
        String dept = resolveDepartment(auth, null);
        List<String> tmValues = resolveTeamValues(auth, null);

        List<Expense> allExpenses = expenseService.findFiltered(List.of(), cat, List.of(), null, null, dept, tmValues);
        List<Budget> allBudgets = budgetService.findFiltered(List.of(), cat, List.of(), dept, tmValues);

        String filename = "경비예산_전체백업_" + java.time.LocalDate.now() + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8));

        excelService.exportExcel(allExpenses, allBudgets, response.getOutputStream());
    }

    // ==================== Helpers ====================

    private boolean isAdmin(Authentication auth) {
        return hasRole(auth, "ROLE_ADMIN");
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role));
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

    /**
     * 권한 레벨에 따라 category(=company) 강제 필터 반환
     * ROLE_COMPANY/DEPARTMENT/TEAM → 자기 회사(category)만
     * ROLE_ADMIN → null (전체)
     */
    private String resolveCategory(Authentication auth, String category) {
        if (isAdmin(auth)) return category;
        return getUserCompany(auth);
    }

    /**
     * 권한 레벨에 따라 department 강제 필터 반환
     * ROLE_DEPARTMENT/TEAM → 자기 실
     * ROLE_COMPANY → 파라미터 그대로 (회사 내 자유 선택)
     * ROLE_ADMIN → 파라미터 그대로
     */
    private String resolveDepartment(Authentication auth, String department) {
        String role = getUserRole(auth);
        if ("ROLE_ADMIN".equals(role) || "ROLE_COMPANY".equals(role)) return department;
        return getUserDepartment(auth); // DEPARTMENT, TEAM
    }

    /**
     * 권한 레벨에 따라 team 강제 필터 반환 (List 버전)
     * ROLE_TEAM → 자기 팀만 (단일)
     * ROLE_DEPARTMENT → 파라미터 그대로 (실 내 자유 선택, 실(자체) 포함 가능)
     * ROLE_COMPANY/ADMIN → 파라미터 그대로
     */
    private List<String> resolveTeamValues(Authentication auth, List<String> team) {
        String role = getUserRole(auth);
        if ("ROLE_TEAM".equals(role)) {
            String myTeam = getUserTeam(auth);
            return myTeam != null ? List.of(myTeam) : List.of();
        }
        // DEPARTMENT/COMPANY/ADMIN → 사용자가 선택한 팀 리스트 그대로
        if (team == null) return List.of();
        return team.stream().filter(t -> t != null && !t.isEmpty()).toList();
    }

    private boolean canAccessExpense(Authentication auth, Expense expense) {
        if (isAdmin(auth)) return true;
        String role = getUserRole(auth);
        String comp = getUserCompany(auth);
        String dept = getUserDepartment(auth);
        String tm = getUserTeam(auth);

        // 회사 체크
        if (comp != null && !comp.equals(expense.getCategory())) return false;
        if ("ROLE_COMPANY".equals(role)) return true;
        // 실 체크
        if (dept != null && !dept.equals(expense.getDepartment())) return false;
        if ("ROLE_DEPARTMENT".equals(role)) return true;
        // 팀 체크
        return Objects.equals(tm, expense.getTeam());
    }

    private boolean canAccessBudget(Authentication auth, Budget budget) {
        if (isAdmin(auth)) return true;
        String role = getUserRole(auth);
        String comp = getUserCompany(auth);
        String dept = getUserDepartment(auth);
        String tm = getUserTeam(auth);

        if (comp != null && !comp.equals(budget.getCategory())) return false;
        if ("ROLE_COMPANY".equals(role)) return true;
        if (dept != null && !dept.equals(budget.getDepartment())) return false;
        if ("ROLE_DEPARTMENT".equals(role)) return true;
        return Objects.equals(tm, budget.getTeam());
    }

    private boolean isOwnTeamExpense(Authentication auth, Expense expense) {
        return canAccessExpense(auth, expense);
    }

    private boolean isOwnTeamBudget(Authentication auth, Budget budget) {
        return canAccessBudget(auth, budget);
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    private String buildRedirect(String ym, String category, String division, String department, String team) {
        return "redirect:/expenses?ym=" + enc(ym) + "&category=" + enc(category) + "&division=" + enc(division)
             + "&department=" + enc(department) + "&team=" + enc(team);
    }

    private String enc(String value) {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
