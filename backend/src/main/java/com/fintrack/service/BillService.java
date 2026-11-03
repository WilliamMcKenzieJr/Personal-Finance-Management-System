package com.fintrack.service;

import com.fintrack.model.Bill;
import com.fintrack.model.Enums.ExpenseCategory;
import com.fintrack.model.Expense;
import com.fintrack.model.User;
import com.fintrack.repository.BillRepository;
import com.fintrack.repository.ExpenseRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.web.ApiException;
import com.fintrack.web.dto.FinanceDtos.BillRequest;
import com.fintrack.web.dto.FinanceDtos.BillResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class BillService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final BillRepository bills;
    private final ExpenseRepository expenses;
    private final UserRepository users;
    private final FinanceService finance;

    public BillService(BillRepository bills, ExpenseRepository expenses, UserRepository users, FinanceService finance) {
        this.bills = bills;
        this.expenses = expenses;
        this.users = users;
        this.finance = finance;
    }

    public List<BillResponse> list(Long userId) {
        return bills.findByUserIdOrderByLabel(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public BillResponse create(Long userId, BillRequest request) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        Bill bill = new Bill();
        bill.setUser(user);
        apply(bill, request, true);
        bills.save(bill);
        syncDownPayment(bill);
        return toResponse(bill);
    }

    @Transactional
    public BillResponse update(Long userId, Long id, BillRequest request) {
        Bill bill = find(userId, id);
        apply(bill, request, false);
        syncDownPayment(bill);
        return toResponse(bill);
    }

    /** Deletes the bill and its down payment record. Payments already made stay in the member's costs. */
    @Transactional
    public void delete(Long userId, Long id) {
        Bill bill = find(userId, id);
        if (bill.getDownPaymentExpenseId() != null) {
            expenses.deleteById(bill.getDownPaymentExpenseId());
        }
        bills.delete(bill);
    }

    /** Records this period's payment as a cost and moves the bill to its next due date. */
    @Transactional
    public BillResponse pay(Long userId, Long id) {
        Bill bill = find(userId, id);
        LocalDate today = LocalDate.now();
        LocalDate payableFrom = bill.payableFrom(lastPaid(bill), today);
        if (payableFrom != null) {
            throw ApiException.conflict("This bill is already marked paid for this period. "
                    + "You can mark the next payment from " + DAY.format(payableFrom) + ".");
        }
        Expense payment = new Expense();
        payment.setUser(bill.getUser());
        payment.setDescription("Bill: " + bill.getLabel());
        payment.setAmount(bill.getAmountDue());
        payment.setDate(today);
        payment.setCategory(ExpenseCategory.BILLS);
        payment.setBillId(bill.getId());
        expenses.save(payment);

        bill.setPeriodsPaid(bill.getPeriodsPaid() + 1);
        bills.saveAndFlush(bill);
        finance.checkSavings(userId, today);
        return toResponse(bill);
    }

    /** Reverses the most recent "Mark paid": removes its cost and moves the due date back. */
    @Transactional
    public BillResponse undoPayment(Long userId, Long id) {
        Bill bill = find(userId, id);
        Expense last = expenses.findFirstByBillIdAndCategoryOrderByDateDescIdDesc(bill.getId(), ExpenseCategory.BILLS)
                .orElseThrow(() -> ApiException.badRequest("There is no payment to undo for this bill."));
        expenses.delete(last);
        bill.setPeriodsPaid(Math.max(0, bill.getPeriodsPaid() - 1));
        bills.saveAndFlush(bill);
        return toResponse(bill);
    }

    /**
     * Keeps a bill consistent when one of its costs is deleted from the Costs page: a deleted
     * payment moves the due date back, a deleted down payment clears it from the bill.
     */
    @Transactional
    public void onExpenseDeleted(Expense expense) {
        if (expense.getBillId() == null) {
            return;
        }
        bills.findById(expense.getBillId()).ifPresent(bill -> {
            if (expense.getId().equals(bill.getDownPaymentExpenseId())) {
                bill.setDownPayment(null);
                bill.setDownPaymentExpenseId(null);
            } else if (expense.getCategory() == ExpenseCategory.BILLS) {
                bill.setPeriodsPaid(Math.max(0, bill.getPeriodsPaid() - 1));
            }
        });
    }

    private BillResponse toResponse(Bill bill) {
        return BillResponse.from(bill, lastPaid(bill), LocalDate.now());
    }

    private LocalDate lastPaid(Bill bill) {
        return expenses.findFirstByBillIdAndCategoryOrderByDateDescIdDesc(bill.getId(), ExpenseCategory.BILLS)
                .map(Expense::getDate).orElse(null);
    }

    private Bill find(Long userId, Long id) {
        return bills.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Bill"));
    }

    private void apply(Bill bill, BillRequest r, boolean isNew) {
        // A new due date or schedule restarts the due-date count from that date.
        if (isNew || !r.dueDate().equals(bill.nextDueDate()) || r.recurrence() != bill.getRecurrence()) {
            bill.setFirstDueDate(r.dueDate());
            bill.setPeriodsPaid(0);
        }
        bill.setLabel(r.label().trim());
        bill.setType(r.type());
        bill.setAmountDue(r.amountDue());
        bill.setRecurrence(r.recurrence());
        bill.setDownPayment(r.downPayment());
    }

    /** Keeps a LARGE_PURCHASE cost in step with the bill's down payment. */
    private void syncDownPayment(Bill bill) {
        BigDecimal down = bill.getDownPayment();
        Expense existing = bill.getDownPaymentExpenseId() == null ? null
                : expenses.findById(bill.getDownPaymentExpenseId()).orElse(null);
        if (down == null || down.signum() <= 0) {
            if (existing != null) {
                expenses.delete(existing);
            }
            bill.setDownPayment(null);
            bill.setDownPaymentExpenseId(null);
            return;
        }
        Expense expense = existing != null ? existing : new Expense();
        if (existing == null) {
            expense.setUser(bill.getUser());
            expense.setDate(LocalDate.now());
            expense.setCategory(ExpenseCategory.LARGE_PURCHASE);
            expense.setBillId(bill.getId());
        }
        expense.setDescription("Down payment: " + bill.getLabel());
        expense.setAmount(down);
        expenses.save(expense);
        bill.setDownPaymentExpenseId(expense.getId());
    }
}
