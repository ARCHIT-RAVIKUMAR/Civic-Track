package com.civictrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "feedback")
public class Feedback extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false, unique = true)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "citizen_id", nullable = false)
    private User citizen;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "comments", length = 1000)
    private String comments;

    protected Feedback() {
    }

    public Feedback(Complaint complaint, User citizen, int rating, String comments) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        this.complaint = Objects.requireNonNull(complaint);
        this.citizen = Objects.requireNonNull(citizen);
        this.rating = rating;
        this.comments = comments == null || comments.isBlank() ? null : comments.trim();
    }

    public Complaint getComplaint() {
        return complaint;
    }

    public User getCitizen() {
        return citizen;
    }

    public int getRating() {
        return rating;
    }

    public String getComments() {
        return comments;
    }
}
