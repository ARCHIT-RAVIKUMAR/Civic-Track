package com.civictrack.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of a complaint. The enum itself knows which moves are legal, so the rule
 * lives in ONE place:
 *
 * SUBMITTED -> UNDER_REVIEW -> ASSIGNED -> IN_PROGRESS -> RESOLVED -> CLOSED
 *      \___________\__> REJECTED (with a reason)       RESOLVED -> IN_PROGRESS (sent back)
 */
public enum ComplaintStatus {
    SUBMITTED("Submitted", "secondary"),
    UNDER_REVIEW("Under Review", "info"),
    ASSIGNED("Assigned", "primary"),
    IN_PROGRESS("In Progress", "warning"),
    RESOLVED("Resolved", "success"),
    CLOSED("Closed", "dark"),
    REJECTED("Rejected", "danger");

    private final String label;
    private final String badgeClass;

    ComplaintStatus(String label, String badgeClass) {
        this.label = label;
        this.badgeClass = badgeClass;
    }

    public String getLabel() {
        return label;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    public Set<ComplaintStatus> allowedNext() {
        return switch (this) {
            case SUBMITTED -> EnumSet.of(UNDER_REVIEW, REJECTED);
            case UNDER_REVIEW -> EnumSet.of(ASSIGNED, REJECTED);
            case ASSIGNED -> EnumSet.of(IN_PROGRESS);
            case IN_PROGRESS -> EnumSet.of(RESOLVED);
            case RESOLVED -> EnumSet.of(CLOSED, IN_PROGRESS);
            case CLOSED, REJECTED -> EnumSet.noneOf(ComplaintStatus.class);
        };
    }

    public boolean canTransitionTo(ComplaintStatus next) {
        return allowedNext().contains(next);
    }

    /** True when no further work can happen on the complaint. */
    public boolean isTerminal() {
        return this == CLOSED || this == REJECTED;
    }
}
