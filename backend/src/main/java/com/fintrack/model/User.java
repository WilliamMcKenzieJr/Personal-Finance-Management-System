package com.fintrack.model;

import com.fintrack.model.Enums.Role;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "app_users")
public class User {

    /**
     * Unique user id. The database assigns it when the account is created; as the primary key it
     * can never be null or duplicated, and it never changes.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, unique = true, updatable = false)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    /** BCrypt hash; the plain password is never stored. */
    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 60)
    private String firstName;

    @Column(nullable = false, length = 60)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.MEMBER;

    /** Share of each month's income (0-100) the member wants to keep as savings. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal savingsPercent = BigDecimal.ZERO;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** Wrong passwords since the last successful sign-in (or since the last lock started). */
    @Column(nullable = false)
    private int failedLoginAttempts;

    /** End of the current 10-minute lock, if one is running. */
    private Instant lockedUntil;

    /** True once a 10-minute lock has happened; the next failure after it locks the account. */
    @Column(nullable = false)
    private boolean temporaryLockUsed;

    /** Locked until an administrator unlocks it. */
    @Column(nullable = false)
    private boolean accountLocked;

    /** Clears every kind of lock, after a successful sign-in or an administrator's unlock. */
    public void clearLoginFailures() {
        failedLoginAttempts = 0;
        lockedUntil = null;
        temporaryLockUsed = false;
        accountLocked = false;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public BigDecimal getSavingsPercent() { return savingsPercent; }
    public void setSavingsPercent(BigDecimal savingsPercent) { this.savingsPercent = savingsPercent; }
    public Instant getCreatedAt() { return createdAt; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
    public boolean isTemporaryLockUsed() { return temporaryLockUsed; }
    public void setTemporaryLockUsed(boolean temporaryLockUsed) { this.temporaryLockUsed = temporaryLockUsed; }
    public boolean isAccountLocked() { return accountLocked; }
    public void setAccountLocked(boolean accountLocked) { this.accountLocked = accountLocked; }
}
