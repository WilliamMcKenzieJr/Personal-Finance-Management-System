package com.fintrack.model;

import jakarta.persistence.*;

import java.time.Instant;

/** Record of an uploaded bank statement and how many transactions were imported from it. */
@Entity
@Table(name = "bank_statements")
public class BankStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false)
    private Instant uploadedAt = Instant.now();

    private int expensesImported;
    private int incomesImported;

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Instant getUploadedAt() { return uploadedAt; }
    public int getExpensesImported() { return expensesImported; }
    public void setExpensesImported(int expensesImported) { this.expensesImported = expensesImported; }
    public int getIncomesImported() { return incomesImported; }
    public void setIncomesImported(int incomesImported) { this.incomesImported = incomesImported; }
}
