package com.bugs.productmanager.service;

import com.bugs.productmanager.model.Budget;
import com.bugs.productmanager.model.Expense;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExcelService {

    private final ExpenseService expenseService;
    private final BudgetService budgetService;

    public ExcelService(ExpenseService expenseService, BudgetService budgetService) {
        this.expenseService = expenseService;
        this.budgetService = budgetService;
    }

    // ==================== Upload ====================

    public record UploadResult(int budgetCount, int expenseCount) {}

    public UploadResult importExcel(MultipartFile file, String ym) throws IOException {
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            List<Map<String, Object>> sections = parseSections(sheet);

            if (sections.isEmpty()) {
                throw new IllegalArgumentException("엑셀에서 섹션을 찾을 수 없습니다. 형식을 확인해주세요.");
            }

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
                Budget budget = new Budget();
                budget.setYm(ym);
                budget.setCategory(cat);
                budget.setDivision(div);
                budget.setMonthlyAmount(BigDecimal.valueOf((long) monthly));
                budget.setPrevRemaining(BigDecimal.valueOf((long) prevRem));
                budgetService.saveOrUpdate(budget);
                budgetCount++;

                // Parse expense rows
                int dataStartRow = startRow + 2;
                int endRow = (i + 1 < sections.size())
                        ? (int) sections.get(i + 1).get("startRow") - 1
                        : sheet.getLastRowNum();

                for (int r = dataStartRow; r <= endRow; r++) {
                    Row dataRow = sheet.getRow(r);
                    if (dataRow == null) continue;

                    Expense expense = parseExpenseRow(dataRow, ym, cat, div, defaultDate);
                    if (expense != null) {
                        expenseService.save(expense);
                        expenseCount++;
                    }
                }
            }

            return new UploadResult(budgetCount, expenseCount);
        }
    }

    private List<Map<String, Object>> parseSections(Sheet sheet) {
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
        return sections;
    }

    private Expense parseExpenseRow(Row dataRow, String ym, String cat, String div, LocalDate defaultDate) {
        Cell dateCell = dataRow.getCell(0);
        Cell purposeCell = dataRow.getCell(1);
        Cell storeCell = dataRow.getCell(2);
        Cell amountCell = dataRow.getCell(3);

        double amount = getNumericValue(amountCell);
        if (amount == 0) return null;

        String dateStr = getCellStringValue(dateCell).trim();
        String purposeStr = getCellStringValue(purposeCell).trim();
        if (dateStr.equalsIgnoreCase("SUM") || purposeStr.equalsIgnoreCase("SUM")) return null;

        boolean hasDate = dateCell != null && (dateCell.getCellType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(dateCell) || !dateStr.isEmpty());
        boolean hasPurpose = !purposeStr.isEmpty();
        String storeStr = getCellStringValue(storeCell).trim();
        boolean hasStore = !storeStr.isEmpty();

        if (!hasDate && !hasPurpose && !hasStore) return null;

        LocalDate expDate = defaultDate;
        if (dateCell != null && dateCell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(dateCell)) {
            java.util.Date d = dateCell.getDateCellValue();
            expDate = d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }

        Expense expense = new Expense();
        expense.setYm(ym);
        expense.setCategory(cat);
        expense.setDivision(div);
        expense.setExpenseDate(expDate);
        expense.setPurpose(purposeStr);
        expense.setStoreName(storeStr);
        expense.setAmount(BigDecimal.valueOf((long) amount));
        return expense;
    }

    // ==================== Download ====================

    public void exportExcel(List<Expense> expenses, List<Budget> budgets, OutputStream out) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("경비예산");

            // Styles
            CellStyle sectionStyle = createSectionStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle amountStyle = createAmountStyle(workbook);
            CellStyle sumStyle = createSumStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);

            // Group expenses by category + division
            Map<String, List<Expense>> grouped = expenses.stream()
                    .collect(Collectors.groupingBy(
                            e -> e.getCategory() + " - " + e.getDivision(),
                            LinkedHashMap::new,
                            Collectors.toList()));

            // Budget lookup map
            Map<String, Budget> budgetMap = new LinkedHashMap<>();
            for (Budget b : budgets) {
                budgetMap.put(b.getCategory() + " - " + b.getDivision(), b);
            }

            // Add budget-only sections
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

                // Section header
                Row sectionRow = sheet.createRow(rowNum++);
                Cell nameCell = sectionRow.createCell(0);
                nameCell.setCellValue(sectionName);
                nameCell.setCellStyle(sectionStyle);
                sectionRow.createCell(1).setCellStyle(sectionStyle);

                Cell monthlyCell = sectionRow.createCell(2);
                monthlyCell.setCellValue(budget != null && budget.getMonthlyAmount() != null
                        ? budget.getMonthlyAmount().doubleValue() : 0);
                monthlyCell.setCellStyle(amountStyle);

                Cell prevCell = sectionRow.createCell(3);
                prevCell.setCellValue(budget != null && budget.getPrevRemaining() != null
                        ? budget.getPrevRemaining().doubleValue() : 0);
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

                rowNum++; // separator
            }

            // Column widths
            for (int i = 0; i < 4; i++) {
                sheet.setColumnWidth(i, i == 0 ? 14 * 256 : i == 3 ? 15 * 256 : 20 * 256);
            }

            workbook.write(out);
        }
    }

    // ==================== Cell Helpers ====================

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

    // ==================== Style Helpers ====================

    private CellStyle createSectionStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createAmountStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
        return style;
    }

    private CellStyle createSumStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
        return style;
    }
}
