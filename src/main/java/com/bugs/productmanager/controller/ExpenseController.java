package com.bugs.productmanager.controller;

import com.bugs.productmanager.model.Budget;
import com.bugs.productmanager.model.Expense;
import com.bugs.productmanager.repository.BudgetRepository;
import com.bugs.productmanager.repository.ExpenseRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/expenses")
public class ExpenseController {

    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;

    public ExpenseController(ExpenseRepository expenseRepository, BudgetRepository budgetRepository) {
        this.expenseRepository = expenseRepository;
        this.budgetRepository = budgetRepository;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) String ym,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String division,
            Authentication auth,
            Model model) {

        List<String> ymList = expenseRepository.findDistinctYm();
        List<String> categoryList = expenseRepository.findDistinctCategory();
        List<String> divisionList = expenseRepository.findDistinctDivision();

        boolean hasYm = ym != null && !ym.isEmpty();
        boolean hasCat = category != null && !category.isEmpty();
        boolean hasDiv = division != null && !division.isEmpty();

        // If no filter at all and data exists, default to latest month
        if (!hasYm && !hasCat && !hasDiv && !ymList.isEmpty()) {
            ym = ymList.get(0);
            hasYm = true;
        }

        List<Expense> expenses;
        if (hasYm && hasCat && hasDiv) {
            expenses = expenseRepository.findByYmAndCategoryAndDivisionOrderByExpenseDateAsc(ym, category, division);
        } else if (hasYm && hasCat) {
            expenses = expenseRepository.findByYmAndCategoryOrderByExpenseDateAsc(ym, category);
        } else if (hasYm && hasDiv) {
            expenses = expenseRepository.findByYmAndDivisionOrderByExpenseDateAsc(ym, division);
        } else if (hasYm) {
            expenses = expenseRepository.findByYmOrderByExpenseDateAsc(ym);
        } else if (hasCat && hasDiv) {
            expenses = expenseRepository.findByCategoryAndDivisionOrderByExpenseDateAsc(category, division);
        } else if (hasCat) {
            expenses = expenseRepository.findByCategoryOrderByExpenseDateAsc(category);
        } else if (hasDiv) {
            expenses = expenseRepository.findByDivisionOrderByExpenseDateAsc(division);
        } else {
            expenses = expenseRepository.findAll();
        }

        BigDecimal totalAmount = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Budget calculation
        BigDecimal monthlyAmount = BigDecimal.ZERO;
        BigDecimal prevRemaining = BigDecimal.ZERO;

        List<Budget> budgets;
        if (hasYm && hasCat && hasDiv) {
            Optional<Budget> b = budgetRepository.findByYmAndCategoryAndDivision(ym, category, division);
            budgets = b.map(List::of).orElse(List.of());
        } else if (hasYm && hasCat) {
            budgets = budgetRepository.findByYmAndCategory(ym, category);
        } else if (hasYm) {
            budgets = budgetRepository.findByYm(ym);
        } else if (hasCat && hasDiv) {
            budgets = budgetRepository.findByCategoryAndDivision(category, division);
        } else if (hasCat) {
            budgets = budgetRepository.findByCategory(category);
        } else if (hasDiv) {
            budgets = budgetRepository.findByDivision(division);
        } else {
            budgets = budgetRepository.findAll();
        }

        for (Budget b : budgets) {
            monthlyAmount = monthlyAmount.add(b.getMonthlyAmount() != null ? b.getMonthlyAmount() : BigDecimal.ZERO);
            prevRemaining = prevRemaining.add(b.getPrevRemaining() != null ? b.getPrevRemaining() : BigDecimal.ZERO);
        }

        BigDecimal budgetTotal = monthlyAmount.add(prevRemaining);
        BigDecimal remaining = budgetTotal.subtract(totalAmount);

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

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
        model.addAttribute("selectedYm", ym);
        model.addAttribute("selectedCategory", category != null ? category : "");
        model.addAttribute("selectedDivision", division != null ? division : "");
        model.addAttribute("isAdmin", isAdmin);

        // Budget list uses same budgets already calculated
        model.addAttribute("budgetList", budgets);

        return "expense/list";
    }

    @GetMapping("/new")
    public String createForm(Model model, Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            return "redirect:/expenses";
        }
        model.addAttribute("expense", new Expense());
        model.addAttribute("categoryList", expenseRepository.findDistinctCategory());
        model.addAttribute("divisionList", expenseRepository.findDistinctDivision());
        return "expense/form";
    }

    @PostMapping
    public String save(@ModelAttribute Expense expense) {
        expenseRepository.save(expense);
        return buildRedirect(expense.getYm(), expense.getCategory(), expense.getDivision());
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid expense Id: " + id));
        model.addAttribute("expense", expense);
        model.addAttribute("categoryList", expenseRepository.findDistinctCategory());
        model.addAttribute("divisionList", expenseRepository.findDistinctDivision());
        return "expense/form";
    }

    @GetMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @RequestParam(required = false) String ym,
                         @RequestParam(required = false) String category,
                         @RequestParam(required = false) String division) {
        Expense expense = expenseRepository.findById(id).orElse(null);
        String redirectYm = (ym != null && !ym.isEmpty()) ? ym : (expense != null ? expense.getYm() : "");
        expenseRepository.deleteById(id);
        return buildRedirect(redirectYm, category, division);
    }

    @GetMapping("/budget/new")
    public String budgetForm(Model model) {
        model.addAttribute("budget", new Budget());
        return "expense/budget-form";
    }

    @GetMapping("/budget/{id}/edit")
    public String budgetEditForm(@PathVariable Long id, Model model) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid budget Id: " + id));
        model.addAttribute("budget", budget);
        return "expense/budget-form";
    }

    @PostMapping("/budget")
    public String saveBudget(@ModelAttribute Budget budget) {
        if (budget.getId() == null) {
            Optional<Budget> existing = budgetRepository.findByYmAndCategoryAndDivision(
                    budget.getYm(), budget.getCategory(), budget.getDivision());
            if (existing.isPresent()) {
                Budget b = existing.get();
                b.setMonthlyAmount(budget.getMonthlyAmount());
                b.setPrevRemaining(budget.getPrevRemaining());
                budgetRepository.save(b);
                return buildRedirect(b.getYm(), b.getCategory(), b.getDivision());
            }
        }
        budgetRepository.save(budget);
        return buildRedirect(budget.getYm(), budget.getCategory(), budget.getDivision());
    }

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

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);

            // Parse sections from the Excel
            List<Map<String, Object>> sections = new ArrayList<>();

            for (Row row : sheet) {
                Cell aCell = row.getCell(0);
                if (aCell == null) continue;
                String aVal = getCellStringValue(aCell).trim();
                if (aVal.isEmpty()) continue;

                String cat = null;
                String div = null;

                if (aVal.contains("LINK") && (aVal.contains("- 경비") || aVal.contains("-경비"))) {
                    cat = "LINK"; div = "경비";
                } else if (aVal.contains("LINK") && (aVal.contains("- 플젝") || aVal.contains("-플젝"))) {
                    cat = "LINK"; div = "플젝";
                } else if (aVal.contains("LINK") && (aVal.contains("- 대외") || aVal.contains("-대외"))) {
                    cat = "LINK"; div = "대외";
                } else if (aVal.contains("LINK") && aVal.contains("출장")) {
                    cat = "LINK"; div = "출장";
                } else if (aVal.contains("BUGS") && (aVal.contains("- 경비") || aVal.contains("-경비"))) {
                    cat = "BUGS"; div = "경비";
                } else if (aVal.contains("BUGS") && (aVal.contains("- 대외") || aVal.contains("-대외"))) {
                    cat = "BUGS"; div = "대외";
                } else if (aVal.contains("BUGS") && (aVal.contains("- 임원") || aVal.contains("-임원"))) {
                    cat = "BUGS"; div = "임원";
                } else if (aVal.contains("임원") && aVal.contains("LINK") && !aVal.contains("BUGS")
                        && !aVal.contains("날짜") && !aVal.toUpperCase().contains("SUM")) {
                    cat = "LINK"; div = "임원";
                }

                if (cat != null) {
                    double monthly = getNumericValue(row.getCell(2));
                    double prevRem = getNumericValue(row.getCell(3));
                    Map<String, Object> sec = new LinkedHashMap<>();
                    sec.put("cat", cat);
                    sec.put("div", div);
                    sec.put("monthly", monthly);
                    sec.put("prev", prevRem);
                    sec.put("startRow", row.getRowNum());
                    sections.add(sec);
                }
            }

            if (sections.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMsg", "엑셀에서 섹션을 찾을 수 없습니다. 형식을 확인해주세요.");
                redirectAttributes.addFlashAttribute("ym", ym);
                return "redirect:/expenses/upload";
            }

            // Parse default date (last day of month)
            YearMonth yearMonth = YearMonth.parse(ym);
            LocalDate defaultDate = yearMonth.atEndOfMonth();

            int budgetCount = 0;
            int expenseCount = 0;

            for (int i = 0; i < sections.size(); i++) {
                Map<String, Object> sec = sections.get(i);
                String cat = (String) sec.get("cat");
                String div = (String) sec.get("div");
                double monthly = (double) sec.get("monthly");
                double prevRem = (double) sec.get("prev");
                int startRow = (int) sec.get("startRow");

                // Save/update budget
                Optional<Budget> existingBudget = budgetRepository.findByYmAndCategoryAndDivision(ym, cat, div);
                if (existingBudget.isPresent()) {
                    Budget b = existingBudget.get();
                    b.setMonthlyAmount(BigDecimal.valueOf((long) monthly));
                    b.setPrevRemaining(BigDecimal.valueOf((long) prevRem));
                    budgetRepository.save(b);
                } else {
                    Budget b = new Budget();
                    b.setYm(ym);
                    b.setCategory(cat);
                    b.setDivision(div);
                    b.setMonthlyAmount(BigDecimal.valueOf((long) monthly));
                    b.setPrevRemaining(BigDecimal.valueOf((long) prevRem));
                    budgetRepository.save(b);
                }
                budgetCount++;

                // Parse expense rows: start 2 rows after section header (skip column header row)
                int dataStartRow = startRow + 2;
                int endRow = (i + 1 < sections.size()) ? (int) sections.get(i + 1).get("startRow") - 1 : sheet.getLastRowNum();

                for (int r = dataStartRow; r <= endRow; r++) {
                    Row dataRow = sheet.getRow(r);
                    if (dataRow == null) continue;

                    Cell dateCell = dataRow.getCell(0);
                    Cell purposeCell = dataRow.getCell(1);
                    Cell storeCell = dataRow.getCell(2);
                    Cell amountCell = dataRow.getCell(3);

                    // Skip if no amount
                    double amount = getNumericValue(amountCell);
                    if (amount == 0) continue;

                    // Skip SUM rows
                    String dateStr = getCellStringValue(dateCell).trim();
                    String purposeStr = getCellStringValue(purposeCell).trim();
                    if (dateStr.equalsIgnoreCase("SUM") || purposeStr.equalsIgnoreCase("SUM")) continue;

                    // Must have date, purpose, or store
                    boolean hasDate = dateCell != null && (dateCell.getCellType() == CellType.NUMERIC
                            && DateUtil.isCellDateFormatted(dateCell) || !dateStr.isEmpty());
                    boolean hasPurpose = !purposeStr.isEmpty();
                    String storeStr = getCellStringValue(storeCell).trim();
                    boolean hasStore = !storeStr.isEmpty();

                    if (!hasDate && !hasPurpose && !hasStore) continue;

                    // Parse date
                    LocalDate expDate = defaultDate;
                    if (dateCell != null && dateCell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(dateCell)) {
                        java.util.Date d = dateCell.getDateCellValue();
                        expDate = d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                    }

                    Expense expense = new Expense();
                    expense.setYm(ym);
                    expense.setCategory(cat);
                    expense.setDivision(div);
                    expense.setExpenseDate(expDate);
                    expense.setPurpose(purposeStr);
                    expense.setStoreName(storeStr);
                    expense.setAmount(BigDecimal.valueOf((long) amount));
                    expenseRepository.save(expense);
                    expenseCount++;
                }
            }

            redirectAttributes.addFlashAttribute("successMsg",
                    "업로드 완료! 예산 " + budgetCount + "건, 경비 " + expenseCount + "건 등록되었습니다.");
            redirectAttributes.addFlashAttribute("ym", ym);
            return "redirect:/expenses/upload";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "업로드 실패: " + e.getMessage());
            redirectAttributes.addFlashAttribute("ym", ym);
            return "redirect:/expenses/upload";
        }
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                }
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield String.valueOf(cell.getNumericCellValue());
                } catch (Exception ex) {
                    try {
                        yield cell.getStringCellValue();
                    } catch (Exception ex2) {
                        yield "";
                    }
                }
            }
            default -> "";
        };
    }

    private double getNumericValue(Cell cell) {
        if (cell == null) return 0;
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING -> {
                try {
                    yield Double.parseDouble(cell.getStringCellValue().replace(",", "").trim());
                } catch (NumberFormatException e) {
                    yield 0;
                }
            }
            case FORMULA -> {
                try {
                    yield cell.getNumericCellValue();
                } catch (Exception e) {
                    yield 0;
                }
            }
            default -> 0;
        };
    }

    @GetMapping("/download")
    public void downloadExcel(
            @RequestParam(required = false) String ym,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String division,
            HttpServletResponse response) throws IOException {

        boolean hasYm = ym != null && !ym.isEmpty();
        boolean hasCat = category != null && !category.isEmpty();
        boolean hasDiv = division != null && !division.isEmpty();

        // Get filtered expenses
        List<Expense> expenses;
        if (hasYm && hasCat && hasDiv) {
            expenses = expenseRepository.findByYmAndCategoryAndDivisionOrderByExpenseDateAsc(ym, category, division);
        } else if (hasYm && hasCat) {
            expenses = expenseRepository.findByYmAndCategoryOrderByExpenseDateAsc(ym, category);
        } else if (hasYm && hasDiv) {
            expenses = expenseRepository.findByYmAndDivisionOrderByExpenseDateAsc(ym, division);
        } else if (hasYm) {
            expenses = expenseRepository.findByYmOrderByExpenseDateAsc(ym);
        } else if (hasCat && hasDiv) {
            expenses = expenseRepository.findByCategoryAndDivisionOrderByExpenseDateAsc(category, division);
        } else if (hasCat) {
            expenses = expenseRepository.findByCategoryOrderByExpenseDateAsc(category);
        } else if (hasDiv) {
            expenses = expenseRepository.findByDivisionOrderByExpenseDateAsc(division);
        } else {
            expenses = expenseRepository.findAll();
        }

        // Get filtered budgets
        List<Budget> budgets;
        if (hasYm && hasCat && hasDiv) {
            Optional<Budget> b = budgetRepository.findByYmAndCategoryAndDivision(ym, category, division);
            budgets = b.map(List::of).orElse(List.of());
        } else if (hasYm && hasCat) {
            budgets = budgetRepository.findByYmAndCategory(ym, category);
        } else if (hasYm) {
            budgets = budgetRepository.findByYm(ym);
        } else if (hasCat && hasDiv) {
            budgets = budgetRepository.findByCategoryAndDivision(category, division);
        } else if (hasCat) {
            budgets = budgetRepository.findByCategory(category);
        } else if (hasDiv) {
            budgets = budgetRepository.findByDivision(division);
        } else {
            budgets = budgetRepository.findAll();
        }

        // Build filename
        String filename = "경비예산";
        if (hasYm) filename += "_" + ym.replace("-", "");
        if (hasCat) filename += "_" + category;
        filename += ".xlsx";

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8));

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("경비예산");

            // Styles
            CellStyle sectionStyle = workbook.createCellStyle();
            Font sectionFont = workbook.createFont();
            sectionFont.setBold(true);
            sectionFont.setFontHeightInPoints((short) 12);
            sectionStyle.setFont(sectionFont);
            sectionStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            sectionStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle amountStyle = workbook.createCellStyle();
            amountStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));

            CellStyle sumStyle = workbook.createCellStyle();
            Font sumFont = workbook.createFont();
            sumFont.setBold(true);
            sumStyle.setFont(sumFont);
            sumStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));

            // Group expenses by category + division
            Map<String, List<Expense>> grouped = expenses.stream()
                    .collect(Collectors.groupingBy(
                            e -> e.getCategory() + " - " + e.getDivision(),
                            LinkedHashMap::new,
                            Collectors.toList()));

            // Create budget lookup map
            Map<String, Budget> budgetMap = new LinkedHashMap<>();
            for (Budget b : budgets) {
                budgetMap.put(b.getCategory() + " - " + b.getDivision(), b);
            }

            // Also add budget sections that have no expenses
            for (Budget b : budgets) {
                String key = b.getCategory() + " - " + b.getDivision();
                if (!grouped.containsKey(key)) {
                    grouped.put(key, new ArrayList<>());
                }
            }

            int rowNum = 0;

            for (Map.Entry<String, List<Expense>> entry : grouped.entrySet()) {
                String sectionName = entry.getKey();
                List<Expense> sectionExpenses = entry.getValue();
                Budget budget = budgetMap.get(sectionName);

                // Section header row: section name + budget info
                Row sectionRow = sheet.createRow(rowNum++);
                Cell nameCell = sectionRow.createCell(0);
                nameCell.setCellValue(sectionName);
                nameCell.setCellStyle(sectionStyle);

                sectionRow.createCell(1).setCellStyle(sectionStyle);

                Cell monthlyCell = sectionRow.createCell(2);
                monthlyCell.setCellValue(budget != null && budget.getMonthlyAmount() != null ? budget.getMonthlyAmount().doubleValue() : 0);
                monthlyCell.setCellStyle(amountStyle);

                Cell prevCell = sectionRow.createCell(3);
                prevCell.setCellValue(budget != null && budget.getPrevRemaining() != null ? budget.getPrevRemaining().doubleValue() : 0);
                prevCell.setCellStyle(amountStyle);

                // Column headers
                Row headerRow = sheet.createRow(rowNum++);
                String[] headers = {"날짜", "용도", "상호", "금액"};
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                // Data rows
                BigDecimal sectionTotal = BigDecimal.ZERO;
                for (Expense exp : sectionExpenses) {
                    Row dataRow = sheet.createRow(rowNum++);

                    Cell dc = dataRow.createCell(0);
                    if (exp.getExpenseDate() != null) {
                        dc.setCellValue(java.sql.Date.valueOf(exp.getExpenseDate()));
                        dc.setCellStyle(dateStyle);
                    }

                    dataRow.createCell(1).setCellValue(exp.getPurpose() != null ? exp.getPurpose() : "");
                    dataRow.createCell(2).setCellValue(exp.getStoreName() != null ? exp.getStoreName() : "");

                    Cell ac = dataRow.createCell(3);
                    ac.setCellValue(exp.getAmount() != null ? exp.getAmount().doubleValue() : 0);
                    ac.setCellStyle(amountStyle);

                    sectionTotal = sectionTotal.add(exp.getAmount() != null ? exp.getAmount() : BigDecimal.ZERO);
                }

                // SUM row
                Row sumRow = sheet.createRow(rowNum++);
                sumRow.createCell(0).setCellValue("SUM");
                Cell sumCell = sumRow.createCell(3);
                sumCell.setCellValue(sectionTotal.doubleValue());
                sumCell.setCellStyle(sumStyle);

                // Empty row separator
                rowNum++;
            }

            // Auto-size columns
            for (int i = 0; i < 4; i++) {
                sheet.setColumnWidth(i, i == 0 ? 14 * 256 : i == 3 ? 15 * 256 : 20 * 256);
            }

            workbook.write(response.getOutputStream());
        }
    }

    private String buildRedirect(String ym, String category, String division) {
        return "redirect:/expenses?ym=" + enc(ym) + "&category=" + enc(category) + "&division=" + enc(division);
    }

    private String enc(String value) {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @GetMapping("/budget/{id}/delete")
    public String deleteBudget(@PathVariable Long id,
                               @RequestParam(required = false) String ym,
                               @RequestParam(required = false) String category,
                               @RequestParam(required = false) String division,
                               Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            return "redirect:/expenses";
        }
        Budget budget = budgetRepository.findById(id).orElse(null);
        String redirectYm = (ym != null && !ym.isEmpty()) ? ym : (budget != null ? budget.getYm() : "");
        budgetRepository.deleteById(id);
        return buildRedirect(redirectYm, category, division);
    }
}
