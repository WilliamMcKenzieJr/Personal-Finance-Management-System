package com.fintrack.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A single payment, or a recurring one when {@code frequency} is set. */
@Entity
@Table(name = "incomes")
public class Income {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 100)
    private String source;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** Date received, or the first payment date for recurring income. */
    @Column(nullable = false)
    private LocalDate date;

    /** Null for one-off income. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Recurrence frequency;

    /** Optional last date a recurring income is paid. */
    private LocalDate endDate;

    private Long statementId;

    public boolean isRecurring() {
        return frequency != null;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Recurrence getFrequency() { return frequency; }
    public void setFrequency(Recurrence frequency) { this.frequency = frequency; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Long getStatementId() { return statementId; }
    public void setStatementId(Long statementId) { this.statementId = statementId; }
}
