package com.civictrack.enums;

/** The four kinds of user in CivicTrack. Using an enum means a typo like "CITZEN" cannot compile. */
public enum Role {
    CITIZEN("Citizen"),
    AUTHORITY("Municipal Authority"),
    FIELD_WORKER("Field Worker"),
    ADMIN("Administrator");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Spring Security expects role names prefixed with ROLE_. */
    public String authority() {
        return "ROLE_" + name();
    }
}
