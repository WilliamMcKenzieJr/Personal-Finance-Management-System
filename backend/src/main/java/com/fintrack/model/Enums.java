package com.fintrack.model;

/** Holder for the small enums used across the domain model. */
public final class Enums {

    private Enums() {
    }

    public enum Role {
        MEMBER, ADMIN, OWNER
    }

    /** Spending categories. Every cost belongs to exactly one. */
    public enum ExpenseCategory {
        BILLS, GROCERIES, TRANSPORTATION, ENTERTAINMENT, LARGE_PURCHASE
    }

    /** Kinds of recurring bills. */
    public enum BillType {
        UTILITIES, RENT, LOAN, TRANSPORTATION_PAYMENT, SUBSCRIPTION
    }

    public enum RequestStatus {
        PENDING, APPROVED, REJECTED
    }

    public enum NotificationType {
        BILL_DUE, SAVINGS_WARNING, GOAL_REACHED, PROFILE
    }

    /** Changes to a user's profile or account, recorded in the audit log. */
    public enum AuditAction {
        ACCOUNT_CREATED, PROFILE_UPDATED, CHANGE_REQUESTED, CHANGE_REQUEST_APPROVED, CHANGE_REQUEST_REJECTED,
        ROLE_CHANGED, SAVINGS_CHANGED, PASSWORD_CHANGED, ACCOUNT_LOCKED, ACCOUNT_UNLOCKED, ACCOUNT_DELETED
    }

    /**
     * Result of a logged sign-in attempt. TEMPORARILY_LOCKED and ACCOUNT_LOCKED are no longer
     * recorded (attempts on locked accounts aren't logged) but stay so older entries still load.
     */
    public enum LoginOutcome {
        SUCCESS, WRONG_PASSWORD, UNKNOWN_USER, TEMPORARILY_LOCKED, ACCOUNT_LOCKED
    }

    public enum SupportStatus {
        OPEN, RESOLVED
    }
}
