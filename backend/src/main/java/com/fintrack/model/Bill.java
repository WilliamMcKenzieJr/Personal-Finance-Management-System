package com.fintrack.model;

import com.fintrack.model.Enums.BillType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A recurring bill, optionally with a one-time down payment. A bill stays active until the
 * member deletes it.
 */
@Entity
@Table(name = "bills")
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Optimistic lock: two "Mark paid" clicks racing each other can't both succeed. */
    @Version
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 100)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BillType type;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amountDue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Recurrence recurrence = Recurrence.MONTHLY;

    /** First due date; later due dates are counted from here. */
    @Column(nullable = false)
    private LocalDate firstDueDate;

    /** Number of periods already paid. The next due date is the occurrence at this index. */
    @Column(nullable = false)
    private int periodsPaid;

    @Column(precision = 14, scale = 2)
    private BigDecimal downPayment;

    /** The expense that records the down payment, so edits to the bill keep it in sync. */
    private Long downPaymentExpenseId;

    public LocalDate nextDueDate() {
        return recurrence.nth(firstDueDate, periodsPaid);
    }

    /**
     * Earliest date the next period may be marked paid, or null if it can be marked paid today.
     * A period can't be paid more than one period ahead, and monthly-or-longer bills can't be paid
     * twice in the same calendar month. Overdue periods can always be caught up.
     */
    public LocalDate payableFrom(LocalDate lastPaid, LocalDate today) {
        LocalDate next = nextDueDate();
        if (next.isBefore(today)) {
            return null;
        }
        LocalDate from = recurrence.nth(firstDueDate, periodsPaid - 1L);
        boolean monthlyOrLonger = recurrence != Recurrence.WEEKLY && recurrence != Recurrence.BIWEEKLY;
        if (monthlyOrLonger && lastPaid != null) {
            LocalDate nextMonth = lastPaid.withDayOfMonth(1).plusMonths(1);
            if (nextMonth.isAfter(from)) {
                from = nextMonth;
            }
        }
        return today.isBefore(from) ? from : null;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public BillType getType() { return type; }
    public void setType(BillType type) { this.type = type; }
    public BigDecimal getAmountDue() { return amountDue; }
    public void setAmountDue(BigDecimal amountDue) { this.amountDue = amountDue; }
    public Recurrence getRecurrence() { return recurrence; }
    public void setRecurrence(Recurrence recurrence) { this.recurrence = recurrence; }
    public LocalDate getFirstDueDate() { return firstDueDate; }
    public void setFirstDueDate(LocalDate firstDueDate) { this.firstDueDate = firstDueDate; }
    public int getPeriodsPaid() { return periodsPaid; }
    public void setPeriodsPaid(int periodsPaid) { this.periodsPaid = periodsPaid; }
    public BigDecimal getDownPayment() { return downPayment; }
    public void setDownPayment(BigDecimal downPayment) { this.downPayment = downPayment; }
    public Long getDownPaymentExpenseId() { return downPaymentExpenseId; }
    public void setDownPaymentExpenseId(Long downPaymentExpenseId) { this.downPaymentExpenseId = downPaymentExpenseId; }
}
