package com.fintrack.service;

import com.fintrack.model.Bill;
import com.fintrack.model.Enums.ExpenseCategory;
import com.fintrack.model.Enums.NotificationType;
import com.fintrack.model.Expense;
import com.fintrack.model.Income;
import com.fintrack.model.User;
import com.fintrack.repository.BillRepository;
import com.fintrack.repository.ExpenseRepository;
import com.fintrack.repository.GoalRepository;
import com.fintrack.repository.IncomeRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.web.ApiException;
import com.fintrack.web.dto.FinanceDtos.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Turns a member's income and costs into totals: the running balance, the monthly savings
 * check, purchase planning and date-range queries.
 */
@Service
public class FinanceService {

    private static final LocalDate EPOCH = LocalDate.of(1900, 1, 1);

    private final IncomeRepository incomes;
    private final ExpenseRepository expenses;
    private final BillRepository bills;
    private final GoalRepository goals;
    private final UserRepository users;
    private final NotificationService notifications;

    public FinanceService(IncomeRepository incomes, ExpenseRepository expenses, BillRepository bills,
                          GoalRepository goals, UserRepository users, NotificationService notifications) {
        this.incomes = incomes;
        this.expenses = expenses;
        this.bills = bills;
        this.goals = goals;
        this.users = users;
        this.notifications = notifications;
    }

    /** One payment of an income entry; recurring income produces one per period. */
    public record Occurrence(Income income, LocalDate date) {
    }

    /** All income payments dated between from and to (inclusive), newest first. */
    public List<Occurrence> incomeOccurrences(Long userId, LocalDate from, LocalDate to) {
        List<Occurrence> result = new ArrayList<>();
        for (Income income : incomes.findByUserIdOrderByDateDesc(userId)) {
            if (!income.isRecurring()) {
                if (!income.getDate().isBefore(from) && !income.getDate().isAfter(to)) {
                    result.add(new Occurrence(income, income.getDate()));
                }
                continue;
            }
            LocalDate end = income.getEndDate() != null && income.getEndDate().isBefore(to) ? income.getEndDate() : to;
            for (long n = 0; ; n++) {
                LocalDate date = income.getFrequency().nth(income.getDate(), n);
                if (date.isAfter(end)) {
                    break;
                }
                if (!date.isBefore(from)) {
                    result.add(new Occurrence(income, date));
                }
            }
        }
        result.sort(Comparator.comparing(Occurrence::date).reversed());
        return result;
    }

    public BigDecimal incomeTotal(Long userId, LocalDate from, LocalDate to) {
        return incomeOccurrences(userId, from, to).stream()
                .map(o -> o.income().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Total amount of money: all income received minus all costs, up to and including asOf. */
    public BigDecimal balance(Long userId, LocalDate asOf) {
        return incomeTotal(userId, EPOCH, asOf).subtract(expenses.sumUpTo(userId, asOf));
    }

    /**
     * This month's income and spending against the member's savings percentage. The spending
     * limit is the month's income minus the share set aside for savings.
     */
    public MonthSummary monthSummary(User user, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        BigDecimal income = incomeTotal(user.getId(), from, to);
        BigDecimal spent = expenses.sumBetween(user.getId(), from, to);
        BigDecimal percent = user.getSavingsPercent();
        BigDecimal savingsTarget = Money.percentOf(income, percent);
        BigDecimal limit = income.subtract(savingsTarget);
        boolean overLimit = percent.signum() > 0 && spent.compareTo(limit) > 0;

        Map<ExpenseCategory, BigDecimal> byCategory = new EnumMap<>(ExpenseCategory.class);
        for (ExpenseCategory c : ExpenseCategory.values()) {
            byCategory.put(c, BigDecimal.ZERO);
        }
        for (Object[] row : expenses.sumByCategory(user.getId(), from, to)) {
            byCategory.put((ExpenseCategory) row[0], (BigDecimal) row[1]);
        }
        return new MonthSummary(month.toString(), income, spent, percent, savingsTarget, limit,
                limit.subtract(spent), overLimit, byCategory);
    }

    /**
     * Warns the member (once per month by notification, and every time in the returned text)
     * when costs in the month of {@code date} rise above their savings limit.
     */
    public String checkSavings(Long userId, LocalDate date) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        YearMonth month = YearMonth.from(date);
        MonthSummary summary = monthSummary(user, month);
        if (!summary.overLimit()) {
            return null;
        }
        String message = "Your costs for " + month + " (" + Money.format(summary.spent()) + ") are above your limit of "
                + Money.format(summary.spendingLimit()) + ", which cuts into the "
                + summary.savingsPercent().stripTrailingZeros().toPlainString() + "% of income you set aside for savings.";
        notifications.notify(userId, NotificationType.SAVINGS_WARNING, "savings-" + month, message);
        return message;
    }

    public DashboardResponse dashboard(Long userId) {
        notifications.checkUser(userId);
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        LocalDate today = LocalDate.now();
        BigDecimal totalIncome = incomeTotal(userId, EPOCH, today);
        BigDecimal totalCosts = expenses.sumUpTo(userId, today);
        List<BillResponse> upcoming = bills.findByUserIdOrderByLabel(userId).stream()
                .filter(b -> !b.nextDueDate().isAfter(today.plusDays(14)))
                .sorted(Comparator.comparing(Bill::nextDueDate))
                .map(b -> BillResponse.from(b, expenses
                        .findFirstByBillIdAndCategoryOrderByDateDescIdDesc(b.getId(), ExpenseCategory.BILLS)
                        .map(Expense::getDate).orElse(null), today))
                .toList();
        List<GoalResponse> goalList = goals.findByUserIdOrderByStartDate(userId).stream()
                .map(g -> GoalResponse.from(g, today)).toList();
        return new DashboardResponse(totalIncome.subtract(totalCosts), totalIncome, totalCosts,
                monthSummary(user, YearMonth.from(today)), upcoming, goalList, notifications.unreadCount(userId));
    }

    /** Shows how buying one or more items would change the member's money, without saving anything. */
    public PlanResponse plan(Long userId, PlanRequest request) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        LocalDate today = LocalDate.now();
        LocalDate in30 = today.plusDays(30);

        BigDecimal current = balance(userId, today);
        BigDecimal running = current;
        BigDecimal total = BigDecimal.ZERO;
        List<PlanLine> lines = new ArrayList<>();
        for (PlanItem item : request.items()) {
            running = running.subtract(item.cost());
            total = total.add(item.cost());
            lines.add(new PlanLine(item.name(), item.cost(), running));
        }

        BigDecimal billsDue = BigDecimal.ZERO;
        for (Bill bill : bills.findByUserIdOrderByLabel(userId)) {
            for (int n = bill.getPeriodsPaid(); ; n++) {
                if (bill.getRecurrence().nth(bill.getFirstDueDate(), n).isAfter(in30)) {
                    break;
                }
                billsDue = billsDue.add(bill.getAmountDue());
            }
        }
        BigDecimal incomeDue = incomeTotal(userId, today.plusDays(1), in30);
        BigDecimal projected = running.subtract(billsDue).add(incomeDue);

        MonthSummary month = monthSummary(user, YearMonth.from(today));
        BigDecimal spentAfter = month.spent().add(total);
        boolean affordable = running.signum() >= 0;
        boolean exceeds = user.getSavingsPercent().signum() > 0 && spentAfter.compareTo(month.spendingLimit()) > 0;

        String message;
        if (!affordable) {
            message = "You don't have enough money for this right now: you would be "
                    + Money.format(running.negate()) + " short.";
        } else if (exceeds) {
            message = "You can afford this, but it would take this month's costs "
                    + Money.format(spentAfter.subtract(month.spendingLimit()))
                    + " over your spending limit and cut into your savings.";
        } else {
            message = "You can afford this and stay within your savings plan.";
        }
        if (affordable && projected.signum() < 0) {
            message += " Careful: after bills due in the next 30 days you would be "
                    + Money.format(projected.negate()) + " short.";
        }
        return new PlanResponse(current, total, running, billsDue, incomeDue, projected, lines, month.spent(),
                spentAfter, month.spendingLimit(), affordable, exceeds, message);
    }

    /** Costs, income, or both between two dates, with totals and the balance at the end date. */
    public QueryResponse query(Long userId, QueryType type, LocalDate from, LocalDate to, ExpenseCategory category) {
        if (from.isAfter(to)) {
            throw ApiException.badRequest("The start date must be on or before the end date.");
        }
        List<Expense> costs = category == null
                ? expenses.findByUserIdAndDateBetweenOrderByDateDesc(userId, from, to)
                : expenses.findByUserIdAndCategoryAndDateBetweenOrderByDateDesc(userId, category, from, to);
        List<Occurrence> income = category == null ? incomeOccurrences(userId, from, to) : List.of();

        BigDecimal costTotal = costs.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal incomeTotal = income.stream().map(o -> o.income().getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<QueryEntry> entries = new ArrayList<>();
        if (type != QueryType.INCOME) {
            costs.forEach(e -> entries.add(new QueryEntry(e.getDate(), e.getDescription(), e.getCategory().name(),
                    type == QueryType.TOTAL ? e.getAmount().negate() : e.getAmount())));
        }
        if (type != QueryType.COST) {
            income.forEach(o -> entries.add(new QueryEntry(o.date(), o.income().getSource(),
                    o.income().isRecurring() ? "RECURRING_INCOME" : "INCOME", o.income().getAmount())));
        }
        entries.sort(Comparator.comparing(QueryEntry::date).reversed());
        return new QueryResponse(type, from, to, incomeTotal, costTotal, incomeTotal.subtract(costTotal),
                balance(userId, to), entries);
    }
}
