package com.fintrack.model;

import com.fintrack.model.Enums.SupportStatus;
import jakarta.persistence.*;

import java.time.Instant;

/** A help request sent from the sign-in page, typically by someone whose account is locked. */
@Entity
@Table(name = "support_requests")
public class SupportRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(nullable = false, length = 64)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupportStatus status = SupportStatus.OPEN;

    @Column(length = 50)
    private String resolvedBy;

    private Instant resolvedAt;

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public SupportStatus getStatus() { return status; }
    public void setStatus(SupportStatus status) { this.status = status; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
