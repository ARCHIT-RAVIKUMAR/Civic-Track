package com.civictrack.entity;

import com.civictrack.enums.ActivityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;

/** One event in a user's account history. Never stores passwords or IP addresses. */
@Entity
@Table(name = "activity_log")
public class ActivityLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Who did it, when somebody else acted on this account (e.g. an administrator). May be null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 40)
    private ActivityType type;

    @Column(name = "details", length = 500)
    private String details;

    protected ActivityLog() {
    }

    public ActivityLog(User user, ActivityType type, String details, User actor) {
        this.user = Objects.requireNonNull(user);
        this.type = Objects.requireNonNull(type);
        this.actor = actor;
        this.details = details == null || details.isBlank() ? null
                : (details.length() > 500 ? details.substring(0, 497) + "..." : details.trim());
    }

    public User getUser() {
        return user;
    }

    public User getActor() {
        return actor;
    }

    public ActivityType getType() {
        return type;
    }

    public String getDetails() {
        return details;
    }
}
