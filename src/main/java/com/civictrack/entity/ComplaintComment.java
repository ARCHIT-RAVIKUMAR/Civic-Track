package com.civictrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;

/** A message on a complaint, written by the citizen, the authority or the assigned field worker. */
@Entity
@Table(name = "complaint_comments")
public class ComplaintComment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    protected ComplaintComment() {
    }

    public ComplaintComment(Complaint complaint, User author, String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message is required");
        }
        this.complaint = Objects.requireNonNull(complaint);
        this.author = Objects.requireNonNull(author);
        this.message = message.trim();
    }

    public Complaint getComplaint() {
        return complaint;
    }

    public User getAuthor() {
        return author;
    }

    public String getMessage() {
        return message;
    }
}
