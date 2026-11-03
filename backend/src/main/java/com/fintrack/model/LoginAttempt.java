package com.fintrack.model;

import com.fintrack.model.Enums.LoginOutcome;
import jakarta.persistence.*;

import java.time.Instant;

/** One sign-in attempt, successful or not, with the address it came from. */
@Entity
@Table(name = "login_attempts", indexes = @Index(columnList = "createdAt"))
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** The username as typed, which may not match any account. */
    @Column(nullable = false, length = 100)
    private String usernameEntered;

    /** The matching account, if there is one. */
    private Long userId;

    @Column(nullable = false, length = 64)
    private String ipAddress;

    @Column(length = 255)
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LoginOutcome outcome;

    @Column(length = 255)
    private String detail;

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public String getUsernameEntered() { return usernameEntered; }
    public void setUsernameEntered(String usernameEntered) { this.usernameEntered = usernameEntered; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public LoginOutcome getOutcome() { return outcome; }
    public void setOutcome(LoginOutcome outcome) { this.outcome = outcome; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
