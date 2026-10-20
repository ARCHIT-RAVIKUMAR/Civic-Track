package com.civictrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "assignments")
public class Assignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private User worker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false)
    private User assignedBy;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Assignment() {
    }

    public Assignment(Complaint complaint, User worker, User assignedBy, String notes) {
        this.complaint = Objects.requireNonNull(complaint);
        this.worker = Objects.requireNonNull(worker);
        this.assignedBy = Objects.requireNonNull(assignedBy);
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    public void deactivate() {
        this.active = false;
    }

    public Complaint getComplaint() {
        return complaint;
    }

    public User getWorker() {
        return worker;
    }

    public User getAssignedBy() {
        return assignedBy;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isActive() {
        return active;
    }
}
