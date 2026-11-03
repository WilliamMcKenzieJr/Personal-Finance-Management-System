package com.fintrack.web;

import com.fintrack.model.Enums.ExpenseCategory;
import com.fintrack.security.CurrentUser;
import com.fintrack.service.*;
import com.fintrack.web.dto.FinanceDtos.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * A member's own financial records. Every call is scoped to the signed-in user's id, so
 * no role (including admin and owner) can read another person's finances.
 */
@RestController
@RequestMapping("/api")
public class FinanceController {

    private final CurrentUser currentUser;
    private final BillService bills;
    private final IncomeService incomes;
    private final ExpenseService expenses;
    private final GoalService goals;
    private final FinanceService finance;
    private final StatementService statements;
    private final NotificationService notifications;

    public FinanceController(CurrentUser currentUser, BillService bills, IncomeService incomes,
                             ExpenseService expenses, GoalService goals, FinanceService finance,
                             StatementService statements, NotificationService notifications) {
        this.currentUser = currentUser;
        this.bills = bills;
        this.incomes = incomes;
        this.expenses = expenses;
        this.goals = goals;
        this.finance = finance;
        this.statements = statements;
        this.notifications = notifications;
    }

    // ---- Bills ----

    @GetMapping("/bills")
    public List<BillResponse> bills() {
        return bills.list(currentUser.id());
    }

    @PostMapping("/bills")
    @ResponseStatus(HttpStatus.CREATED)
    public BillResponse createBill(@Valid @RequestBody BillRequest request) {
        return bills.create(currentUser.id(), request);
    }

    @PutMapping("/bills/{id}")
    public BillResponse updateBill(@PathVariable Long id, @Valid @RequestBody BillRequest request) {
        return bills.update(currentUser.id(), id, request);
    }

    @DeleteMapping("/bills/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBill(@PathVariable Long id) {
        bills.delete(currentUser.id(), id);
    }

    @PostMapping("/bills/{id}/pay")
    public BillResponse payBill(@PathVariable Long id) {
        return bills.pay(currentUser.id(), id);
    }

    @PostMapping("/bills/{id}/undo-payment")
    public BillResponse undoBillPayment(@PathVariable Long id) {
        return bills.undoPayment(currentUser.id(), id);
    }

    // ---- Income ----

    @GetMapping("/incomes")
    public List<IncomeResponse> incomes() {
        return incomes.list(currentUser.id());
    }

    @PostMapping("/incomes")
    @ResponseStatus(HttpStatus.CREATED)
    public IncomeResponse createIncome(@Valid @RequestBody IncomeRequest request) {
        return incomes.create(currentUser.id(), request);
    }

    @PutMapping("/incomes/{id}")
    public IncomeResponse updateIncome(@PathVariable Long id, @Valid @RequestBody IncomeRequest request) {
        return incomes.update(currentUser.id(), id, request);
    }

    @DeleteMapping("/incomes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteIncome(@PathVariable Long id) {
        incomes.delete(currentUser.id(), id);
    }

    // ---- Costs ----

    @GetMapping("/expenses")
    public List<ExpenseResponse> expenses() {
        return expenses.list(currentUser.id());
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseSaved createExpense(@Valid @RequestBody ExpenseRequest request) {
        return expenses.create(currentUser.id(), request);
    }

    @PutMapping("/expenses/{id}")
    public ExpenseSaved updateExpense(@PathVariable Long id, @Valid @RequestBody ExpenseRequest request) {
        return expenses.update(currentUser.id(), id, request);
    }

    @DeleteMapping("/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(@PathVariable Long id) {
        expenses.delete(currentUser.id(), id);
    }

    // ---- Goals ----

    @GetMapping("/goals")
    public List<GoalResponse> goals() {
        return goals.list(currentUser.id());
    }

    @PostMapping("/goals")
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse createGoal(@Valid @RequestBody GoalRequest request) {
        return goals.create(currentUser.id(), request);
    }

    @PutMapping("/goals/{id}")
    public GoalResponse updateGoal(@PathVariable Long id, @Valid @RequestBody GoalRequest request) {
        return goals.update(currentUser.id(), id, request);
    }

    @PostMapping("/goals/{id}/contribute")
    public GoalResponse contribute(@PathVariable Long id, @Valid @RequestBody ContributionRequest request) {
        return goals.contribute(currentUser.id(), id, request.amount());
    }

    @DeleteMapping("/goals/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGoal(@PathVariable Long id) {
        goals.delete(currentUser.id(), id);
    }

    // ---- Totals, planning and queries ----

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return finance.dashboard(currentUser.id());
    }

    @PostMapping("/planning")
    public PlanResponse plan(@Valid @RequestBody PlanRequest request) {
        return finance.plan(currentUser.id(), request);
    }

    @GetMapping("/query")
    public QueryResponse query(@RequestParam QueryType type,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(required = false) ExpenseCategory category) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        return finance.query(currentUser.id(), type, start, end, category);
    }

    // ---- Bank statements ----

    @GetMapping("/statements")
    public List<StatementResponse> statements() {
        return statements.list(currentUser.id());
    }

    @PostMapping(value = "/statements/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StatementPreview previewStatement(@RequestParam("file") MultipartFile file) {
        currentUser.id();
        return statements.preview(file);
    }

    @PostMapping("/statements/import")
    @ResponseStatus(HttpStatus.CREATED)
    public StatementResponse importStatement(@Valid @RequestBody StatementImportRequest request) {
        return statements.importRows(currentUser.id(), request);
    }

    // ---- Notifications ----

    @GetMapping("/notifications")
    public List<NotificationResponse> notifications() {
        Long userId = currentUser.id();
        notifications.checkUser(userId);
        return notifications.list(userId);
    }

    @PostMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long id) {
        notifications.markRead(currentUser.id(), id);
    }

    @PostMapping("/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead() {
        notifications.markAllRead(currentUser.id());
    }

    @DeleteMapping("/notifications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNotification(@PathVariable Long id) {
        notifications.delete(currentUser.id(), id);
    }
}
