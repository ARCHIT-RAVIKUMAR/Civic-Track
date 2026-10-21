package com.civictrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;

/** An in-app message ("you have work to do") shown under the bell icon. */
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complaint_id")
    private Complaint complaint;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    protected Notification() {
    }

    public Notification(User recipient, Complaint complaint, String title, String message) {
        this.recipient = Objects.requireNonNull(recipient);
        this.complaint = complaint;
        this.title = abbreviate(title, 150);
        this.message = abbreviate(message, 500);
    }

    private static String abbreviate(String value, int max) {
        String text = value == null || value.isBlank() ? "-" : value.trim();
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    public void markRead() {
        this.read = true;
    }

    public User getRecipient() {
        return recipient;
    }

    public Complaint getComplaint() {
        return complaint;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return read;
    }
}
