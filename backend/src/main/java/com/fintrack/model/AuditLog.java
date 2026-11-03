package com.fintrack.model;

import com.fintrack.model.Enums.AuditAction;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * One change to a user's profile or account. Users are referenced by id and username rather
 * than by foreign key, so the history survives when an account is deleted.
 */
@Entity
@Table(name = "audit_log", indexes = @Index(columnList = "createdAt"))
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Who made the change; null when the system did it (e.g. an automatic lock). */
    private Long actorId;

    @Column(length = 50)
    private String actorUsername;

    @Column(nullable = false)
    private Long targetUserId;

    @Column(nullable = false, length = 50)
    private String targetUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Column(length = 1000)
    private String details;

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public String getActorUsername() { return actorUsername; }
    public void setActorUsername(String actorUsername) { this.actorUsername = actorUsername; }
    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
    public String getTargetUsername() { return targetUsername; }
    public void setTargetUsername(String targetUsername) { this.targetUsername = targetUsername; }
    public AuditAction getAction() { return action; }
    public void setAction(AuditAction action) { this.action = action; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
