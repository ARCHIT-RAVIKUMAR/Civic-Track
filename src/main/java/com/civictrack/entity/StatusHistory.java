package com.civictrack.entity;

import com.civictrack.enums.ComplaintStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;

/** One row per status change (or progress note). Rows are only ever added, never edited. */
@Entity
@Table(name = "complaint_status_history")
public class StatusHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 20)
    private ComplaintStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private ComplaintStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    @Column(name = "notes", length = 1000)
    private String notes;

    protected StatusHistory() {
    }

    public StatusHistory(Complaint complaint, ComplaintStatus oldStatus, ComplaintStatus newStatus,
                         User changedBy, String notes) {
        this.complaint = Objects.requireNonNull(complaint);
        this.oldStatus = oldStatus;
        this.newStatus = Objects.requireNonNull(newStatus);
        this.changedBy = Objects.requireNonNull(changedBy);
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    public Complaint getComplaint() {
        return complaint;
    }

    public ComplaintStatus getOldStatus() {
        return oldStatus;
    }

    public ComplaintStatus getNewStatus() {
        return newStatus;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public String getNotes() {
        return notes;
    }
}
