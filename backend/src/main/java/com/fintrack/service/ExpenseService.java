package com.fintrack.service;

import com.fintrack.model.Expense;
import com.fintrack.repository.ExpenseRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.web.ApiException;
import com.fintrack.web.dto.FinanceDtos.ExpenseRequest;
import com.fintrack.web.dto.FinanceDtos.ExpenseResponse;
import com.fintrack.web.dto.FinanceDtos.ExpenseSaved;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ExpenseService {

    private final ExpenseRepository expenses;
    private final UserRepository users;
    private final FinanceService finance;
    private final BillService bills;

    public ExpenseService(ExpenseRepository expenses, UserRepository users, FinanceService finance, BillService bills) {
        this.expenses = expenses;
        this.users = users;
        this.finance = finance;
        this.bills = bills;
    }

    public List<ExpenseResponse> list(Long userId) {
        return expenses.findByUserIdOrderByDateDescIdDesc(userId).stream().map(ExpenseResponse::from).toList();
    }

    @Transactional
    public ExpenseSaved create(Long userId, ExpenseRequest request) {
        Expense expense = new Expense();
        expense.setUser(users.findById(userId).orElseThrow(() -> ApiException.notFound("User")));
        apply(expense, request);
        expenses.save(expense);
        return new ExpenseSaved(ExpenseResponse.from(expense), finance.checkSavings(userId, expense.getDate()));
    }

    @Transactional
    public ExpenseSaved update(Long userId, Long id, ExpenseRequest request) {
        Expense expense = find(userId, id);
        apply(expense, request);
        expenses.flush();
        return new ExpenseSaved(ExpenseResponse.from(expense), finance.checkSavings(userId, expense.getDate()));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Expense expense = find(userId, id);
        bills.onExpenseDeleted(expense);
        expenses.delete(expense);
    }

    private Expense find(Long userId, Long id) {
        return expenses.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Cost"));
    }

    private void apply(Expense expense, ExpenseRequest r) {
        expense.setDescription(r.description().trim());
        expense.setAmount(r.amount());
        expense.setDate(r.date());
        expense.setCategory(r.category());
    }
}
