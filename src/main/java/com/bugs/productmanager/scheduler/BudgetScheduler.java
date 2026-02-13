package com.bugs.productmanager.scheduler;

import com.bugs.productmanager.model.Budget;
import com.bugs.productmanager.model.Expense;
import com.bugs.productmanager.repository.BudgetRepository;
import com.bugs.productmanager.service.ExpenseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
public class BudgetScheduler {

    private static final Logger log = LoggerFactory.getLogger(BudgetScheduler.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final BudgetRepository budgetRepository;
    private final ExpenseService expenseService;

    public BudgetScheduler(BudgetRepository budgetRepository, ExpenseService expenseService) {
        this.budgetRepository = budgetRepository;
        this.expenseService = expenseService;
    }

    /**
     * 서버 시작 시 현재 월 예산이 없으면 자동 생성
     */
    @PostConstruct
    public void onStartup() {
        log.info("서버 시작 - 월 예산 자동 생성 체크");
        generateMonthlyBudget();
    }

    /**
     * 매월 1일 00:05에 실행
     * 전월 예산을 기반으로 신규 월 예산 자동 생성
     * - 금월예산: 전월 금월예산 그대로 복사
     * - 전월잔여: 전월 예산합계 - 전월 사용금액
     */
    @Scheduled(cron = "0 5 0 1 * *")
    public void generateMonthlyBudget() {
        YearMonth now = YearMonth.now();
        String currentYm = now.format(FMT);
        String prevYm = now.minusMonths(1).format(FMT);

        log.info("========== 월 예산 자동 생성 시작: {} ==========", currentYm);

        // 이미 현재 월 예산이 있으면 스킵
        List<Budget> existing = budgetRepository.findByYm(currentYm);
        if (!existing.isEmpty()) {
            log.info("이미 {} 월 예산이 {}건 존재합니다. 스킵합니다.", currentYm, existing.size());
            return;
        }

        // 전월 예산 조회
        List<Budget> prevBudgets = budgetRepository.findByYm(prevYm);
        if (prevBudgets.isEmpty()) {
            log.info("전월({}) 예산이 없어 자동 생성을 스킵합니다.", prevYm);
            return;
        }

        // 전월 사용금액 계산 (category+division+department+team별)
        List<Expense> prevExpenses = expenseService.findFiltered(prevYm, null, null);
        Map<String, BigDecimal> usedMap = expenseService.calcUsedAmountByBudgetKey(prevExpenses);

        int created = 0;
        for (Budget prev : prevBudgets) {
            Budget newBudget = new Budget();
            newBudget.setYm(currentYm);
            newBudget.setCategory(prev.getCategory());
            newBudget.setDivision(prev.getDivision());
            newBudget.setDepartment(prev.getDepartment());
            newBudget.setTeam(prev.getTeam());

            // 금월예산: 전월 금월예산 그대로
            newBudget.setMonthlyAmount(prev.getMonthlyAmount() != null ? prev.getMonthlyAmount() : BigDecimal.ZERO);

            // 전월잔여: 전월 예산합계 - 전월 사용금액
            BigDecimal prevTotal = prev.getTotalBudget();
            String dept = prev.getDepartment() != null ? prev.getDepartment() : "";
            String tm = prev.getTeam() != null ? prev.getTeam() : "";
            String key = prevYm + "_" + prev.getCategory() + "_" + prev.getDivision() + "_" + dept + "_" + tm;
            BigDecimal prevUsed = usedMap.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal prevRemain = prevTotal.subtract(prevUsed);
            newBudget.setPrevRemaining(prevRemain);

            budgetRepository.save(newBudget);
            created++;

            log.info("  생성: {} / {} / {} / {} / {} → 금월예산={}, 전월잔여={}",
                    currentYm, prev.getCategory(), prev.getDivision(), prev.getDepartment(), prev.getTeam(),
                    newBudget.getMonthlyAmount(), newBudget.getPrevRemaining());
        }

        log.info("========== 월 예산 자동 생성 완료: {}건 ==========", created);
    }
}
