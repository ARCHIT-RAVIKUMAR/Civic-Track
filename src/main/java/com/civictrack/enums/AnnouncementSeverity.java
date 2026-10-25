package com.civictrack.enums;

public enum AnnouncementSeverity {
    INFO("Information", "info", "bi-info-circle"),
    WARNING("Warning", "warning", "bi-exclamation-triangle"),
    URGENT("Urgent", "danger", "bi-exclamation-octagon");

    private final String label;
    private final String alertClass;
    private final String icon;

    AnnouncementSeverity(String label, String alertClass, String icon) {
        this.label = label;
        this.alertClass = alertClass;
        this.icon = icon;
    }

    public String getLabel() {
        return label;
    }

    /** Bootstrap alert colour name. */
    public String getAlertClass() {
        return alertClass;
    }

    public String getIcon() {
        return icon;
    }
}
