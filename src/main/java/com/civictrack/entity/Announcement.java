package com.civictrack.entity;

import com.civictrack.enums.AnnouncementSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/** A notice from the administrators that is shown at the top of the pages until it expires or is switched off. */
@Entity
@Table(name = "announcements")
public class Announcement extends BaseEntity {

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private AnnouncementSeverity severity;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    protected Announcement() {
    }

    public Announcement(String title, String message, AnnouncementSeverity severity,
                        LocalDateTime expiresAt, User createdBy) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message is required");
        }
        this.title = title.trim();
        this.message = message.trim();
        this.severity = Objects.requireNonNull(severity);
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
    }

    public void toggleActive() {
        this.active = !this.active;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public String getExpiresDisplay() {
        return expiresAt == null ? "No end date" : expiresAt.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"));
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public AnnouncementSeverity getSeverity() {
        return severity;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public User getCreatedBy() {
        return createdBy;
    }
}
