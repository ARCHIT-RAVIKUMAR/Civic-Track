package com.civictrack.enums;

public enum Priority {
    LOW("Low", "secondary"),
    MEDIUM("Medium", "info"),
    HIGH("High", "warning"),
    URGENT("Urgent", "danger");

    private final String label;
    private final String badgeClass;

    Priority(String label, String badgeClass) {
        this.label = label;
        this.badgeClass = badgeClass;
    }

    public String getLabel() {
        return label;
    }

    public String getBadgeClass() {
        return badgeClass;
    }
}
