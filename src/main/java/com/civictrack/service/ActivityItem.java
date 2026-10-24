package com.civictrack.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** One line of a user's activity timeline (already converted to plain text, safe to show). */
public class ActivityItem {

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final LocalDateTime time;
    private final String kind;
    private final String icon;
    private final String description;
    private final UUID complaintId;

    public ActivityItem(LocalDateTime time, String kind, String icon, String description, UUID complaintId) {
        this.time = time;
        this.kind = kind;
        this.icon = icon;
        this.description = description;
        this.complaintId = complaintId;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public String getTimeDisplay() {
        return time == null ? "" : time.format(DISPLAY);
    }

    public String getKind() {
        return kind;
    }

    public String getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }

    public UUID getComplaintId() {
        return complaintId;
    }
}
