package com.civictrack.entity;

import com.civictrack.enums.ComplaintStatus;
import com.civictrack.enums.Priority;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.exception.InvalidStatusTransitionException;
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

/**
 * ENCAPSULATION in action: there is deliberately NO setStatus(). The only way to change
 * the status is changeStatus(), which refuses illegal moves. The object protects itself.
 */
@Entity
@Table(name = "complaints")
public class Complaint extends BaseEntity {

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Column(name = "location", nullable = false, length = 255)
    private String location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "citizen_id", nullable = false)
    private User citizen;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ComplaintStatus status = ComplaintStatus.SUBMITTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private Priority priority = Priority.MEDIUM;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "image_filename", length = 100)
    private String imageFilename;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    protected Complaint() {
    }

    public Complaint(String title, String description, String location, Category category, User citizen) {
        this.title = requireText(title, "Title");
        this.description = requireText(description, "Description");
        this.location = requireText(location, "Location");
        this.category = Objects.requireNonNull(category, "Category is required");
        this.citizen = Objects.requireNonNull(citizen, "Citizen is required");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    // ---- behaviour (the only ways to change state) ----

    public void changeStatus(ComplaintStatus next) {
        Objects.requireNonNull(next, "New status is required");
        if (!status.canTransitionTo(next)) {
            throw new InvalidStatusTransitionException(status, next);
        }
        this.status = next;
    }

    public void reject(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("A reason is required to reject a complaint.");
        }
        changeStatus(ComplaintStatus.REJECTED);
        this.rejectionReason = reason.trim();
    }

    public void changePriority(Priority newPriority) {
        this.priority = Objects.requireNonNull(newPriority, "Priority is required");
    }

    /** Sets (or clears) the map position. Both values must be given together and be real coordinates. */
    public void locate(Double lat, Double lng) {
        if (lat == null && lng == null) {
            this.latitude = null;
            this.longitude = null;
            return;
        }
        if (lat == null || lng == null) {
            throw new IllegalArgumentException("Both latitude and longitude are required");
        }
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("Coordinates are out of range");
        }
        this.latitude = lat;
        this.longitude = lng;
    }

    public void attachImage(String storedFilename) {
        this.imageFilename = storedFilename;
    }

    public boolean isOwnedBy(User user) {
        return user != null && citizen.getId() != null && citizen.getId().equals(user.getId());
    }

    // ---- getters ----

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }

    public Category getCategory() {
        return category;
    }

    public User getCitizen() {
        return citizen;
    }

    public ComplaintStatus getStatus() {
        return status;
    }

    public Priority getPriority() {
        return priority;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public String getImageFilename() {
        return imageFilename;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    /**
     * True when the work is still open and older than the target for its priority
     * (Urgent 1 day, High 3, Medium 7, Low 14). The clock stops when the worker marks it resolved.
     */
    public boolean isOverdue() {
        if (getCreatedAt() == null || status.isTerminal() || status == ComplaintStatus.RESOLVED) {
            return false;
        }
        return getCreatedAt().plusDays(priority.getSlaDays()).isBefore(LocalDateTime.now());
    }

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    public boolean hasImage() {
        return imageFilename != null;
    }
}
