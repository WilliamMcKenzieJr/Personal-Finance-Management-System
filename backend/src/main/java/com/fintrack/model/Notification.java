package com.fintrack.model;

import com.fintrack.model.Enums.NotificationType;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "notifications",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "dedupeKey"}))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 500)
    private String message;

    /** Identifies the event (e.g. "bill-7-2026-11-01") so the same reminder is not sent twice. */
    @Column(nullable = false, length = 100)
    private String dedupeKey;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "is_read", nullable = false)
    private boolean read;

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String dedupeKey) { this.dedupeKey = dedupeKey; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}
