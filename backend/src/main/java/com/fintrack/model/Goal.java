package com.fintrack.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Something the member is saving for. The saving plan puts aside {@code monthlyContribution}
 * every month from {@code startDate}; {@code extraSaved} records one-off top-ups.
 */
@Entity
@Table(name = "goals")
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal targetAmount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monthlyContribution;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal extraSaved = BigDecimal.ZERO;

    public BigDecimal savedAmount(LocalDate today) {
        long months = Math.max(0, ChronoUnit.MONTHS.between(startDate, today));
        BigDecimal planned = monthlyContribution.multiply(BigDecimal.valueOf(months));
        return planned.add(extraSaved).min(targetAmount);
    }

    public boolean isReached(LocalDate today) {
        return savedAmount(today).compareTo(targetAmount) >= 0;
    }

    /** Date the plan reaches the target, or null if it never will (no monthly contribution). */
    public LocalDate projectedDate(LocalDate today) {
        BigDecimal remaining = targetAmount.subtract(savedAmount(today));
        if (remaining.signum() <= 0) {
            return today;
        }
        if (monthlyContribution.signum() <= 0) {
            return null;
        }
        long months = ChronoUnit.MONTHS.between(startDate, today);
        long monthsNeeded = remaining.divide(monthlyContribution, 0, RoundingMode.CEILING).longValue();
        return startDate.plusMonths(Math.max(0, months) + monthsNeeded);
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public void setTargetAmount(BigDecimal targetAmount) { this.targetAmount = targetAmount; }
    public BigDecimal getMonthlyContribution() { return monthlyContribution; }
    public void setMonthlyContribution(BigDecimal monthlyContribution) { this.monthlyContribution = monthlyContribution; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public BigDecimal getExtraSaved() { return extraSaved; }
    public void setExtraSaved(BigDecimal extraSaved) { this.extraSaved = extraSaved; }
}
