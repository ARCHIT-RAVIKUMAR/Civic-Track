package com.civictrack.enums;

public enum Priority {
    LOW("Low", "secondary", 14),
    MEDIUM("Medium", "info", 7),
    HIGH("High", "warning", 3),
    URGENT("Urgent", "danger", 1);

    private final String label;
    private final String badgeClass;
    private final int slaDays;

    Priority(String label, String badgeClass, int slaDays) {
        this.label = label;
        this.badgeClass = badgeClass;
        this.slaDays = slaDays;
    }

    public String getLabel() {
        return label;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    /** Target number of days to finish the work, counted from the day the complaint was reported. */
    public int getSlaDays() {
        return slaDays;
    }
}
