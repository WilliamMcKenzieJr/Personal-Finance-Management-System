package com.fintrack.web.dto;

import com.fintrack.model.*;
import com.fintrack.model.Enums.BillType;
import com.fintrack.model.Enums.ExpenseCategory;
import com.fintrack.model.Enums.NotificationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Requests and responses for a member's own financial records. */
public final class FinanceDtos {

    private FinanceDtos() {
    }

    // ---- Bills ----

    public record BillRequest(
            @NotBlank @Size(max = 100) String label,
            @NotNull BillType type,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amountDue,
            @NotNull LocalDate dueDate,
            @NotNull Recurrence recurrence,
            @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal downPayment) {
    }

    /**
     * {@code canPay} is false when this period was already marked paid; {@code payableFrom} then
     * says when the next one can be. {@code lastPaidDate} is null when nothing can be undone.
     */
    public record BillResponse(Long id, String label, BillType type, BigDecimal amountDue, LocalDate nextDueDate,
                               Recurrence recurrence, LocalDate firstDueDate, int periodsPaid,
                               BigDecimal downPayment, LocalDate lastPaidDate, boolean canPay,
                               LocalDate payableFrom, boolean overdue) {
        public static BillResponse from(Bill b, LocalDate lastPaid, LocalDate today) {
            LocalDate next = b.nextDueDate();
            LocalDate payableFrom = b.payableFrom(lastPaid, today);
            return new BillResponse(b.getId(), b.getLabel(), b.getType(), b.getAmountDue(), next,
                    b.getRecurrence(), b.getFirstDueDate(), b.getPeriodsPaid(), b.getDownPayment(), lastPaid,
                    payableFrom == null, payableFrom, next.isBefore(today));
        }
    }

    // ---- Income ----

    public record IncomeRequest(
            @NotBlank @Size(max = 100) String source,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotNull LocalDate date,
            Recurrence frequency,
            LocalDate endDate) {
    }

    public record IncomeResponse(Long id, String source, BigDecimal amount, LocalDate date, boolean recurring,
                                 Recurrence frequency, LocalDate endDate) {
        public static IncomeResponse from(Income i) {
            return new IncomeResponse(i.getId(), i.getSource(), i.getAmount(), i.getDate(), i.isRecurring(),
                    i.getFrequency(), i.getEndDate());
        }
    }

    // ---- Expenses ----

    public record ExpenseRequest(
            @NotBlank @Size(max = 150) String description,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotNull LocalDate date,
            @NotNull ExpenseCategory category) {
    }

    public record ExpenseResponse(Long id, String description, BigDecimal amount, LocalDate date,
                                  ExpenseCategory category, Long billId, Long statementId) {
        public static ExpenseResponse from(Expense e) {
            return new ExpenseResponse(e.getId(), e.getDescription(), e.getAmount(), e.getDate(), e.getCategory(),
                    e.getBillId(), e.getStatementId());
        }
    }

    /** A saved expense plus a warning when it pushed this month's spending into the savings share. */
    public record ExpenseSaved(ExpenseResponse expense, String savingsWarning) {
    }

    // ---- Goals ----

    public record GoalRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal targetAmount,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal monthlyContribution,
            LocalDate startDate) {
    }

    public record ContributionRequest(@NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount) {
    }

    public record GoalResponse(Long id, String name, BigDecimal targetAmount, BigDecimal monthlyContribution,
                               LocalDate startDate, BigDecimal savedAmount, BigDecimal extraSaved,
                               int progressPercent, LocalDate projectedDate, boolean availableToPurchase) {
        public static GoalResponse from(Goal g, LocalDate today) {
            BigDecimal saved = g.savedAmount(today);
            int progress = saved.multiply(BigDecimal.valueOf(100))
                    .divide(g.getTargetAmount(), 0, java.math.RoundingMode.DOWN).intValue();
            return new GoalResponse(g.getId(), g.getName(), g.getTargetAmount(), g.getMonthlyContribution(),
                    g.getStartDate(), saved, g.getExtraSaved(), Math.min(100, progress), g.projectedDate(today),
                    g.isReached(today));
        }
    }

    // ---- Summary, planning and queries ----

    public record MonthSummary(String month, BigDecimal income, BigDecimal spent, BigDecimal savingsPercent,
                               BigDecimal savingsTarget, BigDecimal spendingLimit, BigDecimal remainingBeforeLimit,
                               boolean overLimit, Map<ExpenseCategory, BigDecimal> spentByCategory) {
    }

    public record DashboardResponse(BigDecimal balance, BigDecimal totalIncome, BigDecimal totalCosts,
                                    MonthSummary month, List<BillResponse> upcomingBills,
                                    List<GoalResponse> goals, long unreadNotifications) {
    }

    public record PlanItem(@NotBlank @Size(max = 100) String name,
                           @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal cost) {
    }

    public record PlanRequest(@NotEmpty @Size(max = 50) List<@Valid PlanItem> items) {
    }

    public record PlanLine(String name, BigDecimal cost, BigDecimal balanceAfter) {
    }

    public record PlanResponse(BigDecimal currentBalance, BigDecimal totalCost, BigDecimal balanceAfter,
                               BigDecimal billsDueNext30Days, BigDecimal incomeNext30Days,
                               BigDecimal projectedIn30Days, List<PlanLine> items,
                               BigDecimal monthSpentNow, BigDecimal monthSpentAfter, BigDecimal spendingLimit,
                               boolean affordable, boolean exceedsSavingsLimit, String message) {
    }

    public enum QueryType { COST, INCOME, TOTAL }

    public record QueryEntry(LocalDate date, String label, String category, BigDecimal amount) {
    }

    public record QueryResponse(QueryType type, LocalDate from, LocalDate to, BigDecimal incomeTotal,
                                BigDecimal costTotal, BigDecimal net, BigDecimal balanceAtEnd,
                                List<QueryEntry> entries) {
    }

    // ---- Notifications ----

    public record NotificationResponse(Long id, NotificationType type, String message, Instant createdAt,
                                       boolean read) {
        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getMessage(), n.getCreatedAt(), n.isRead());
        }
    }

    // ---- Bank statements ----

    public enum RowKind { EXPENSE, INCOME }

    public record StatementRow(@NotNull LocalDate date,
                               @NotBlank @Size(max = 150) String description,
                               @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
                               @NotNull RowKind kind,
                               ExpenseCategory category) {
    }

    public record StatementPreview(String fileName, List<StatementRow> rows, List<String> skippedLines) {
    }

    public record StatementImportRequest(@NotBlank @Size(max = 255) String fileName,
                                         @NotEmpty @Size(max = 2000) List<@Valid StatementRow> rows) {
    }

    public record StatementResponse(Long id, String fileName, Instant uploadedAt, int expensesImported,
                                    int incomesImported) {
        public static StatementResponse from(BankStatement s) {
            return new StatementResponse(s.getId(), s.getFileName(), s.getUploadedAt(), s.getExpensesImported(),
                    s.getIncomesImported());
        }
    }
}
