package com.fintrack.model;

import com.fintrack.model.Enums.ExpenseCategory;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Money spent: a purchase, a bill payment, or a down payment. */
@Entity
@Table(name = "expenses", indexes = @Index(columnList = "user_id,date"))
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 150)
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExpenseCategory category;

    /** Set when the expense was created by paying a bill. */
    private Long billId;

    /** Set when the expense was imported from a bank statement. */
    private Long statementId;

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public ExpenseCategory getCategory() { return category; }
    public void setCategory(ExpenseCategory category) { this.category = category; }
    public Long getBillId() { return billId; }
    public void setBillId(Long billId) { this.billId = billId; }
    public Long getStatementId() { return statementId; }
    public void setStatementId(Long statementId) { this.statementId = statementId; }
}
