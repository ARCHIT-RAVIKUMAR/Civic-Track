package com.civictrack.enums;

/** Kinds of account events that are stored in the activity log. */
public enum ActivityType {
    LOGIN_SUCCESS("Logged in", "bi-box-arrow-in-right", "Security"),
    LOGIN_FAILED("Failed login attempt", "bi-shield-exclamation", "Security"),
    ACCOUNT_LOCKED("Account locked after repeated failed logins", "bi-lock", "Security"),
    PASSWORD_CHANGED("Changed their password", "bi-key", "Security"),
    PASSWORD_RESET_BY_ADMIN("Password reset by an administrator", "bi-key", "Security"),
    ACCOUNT_REGISTERED("Registered an account", "bi-person-plus", "Account"),
    ACCOUNT_CREATED_BY_ADMIN("Account created by an administrator", "bi-person-plus", "Account"),
    PROFILE_UPDATED("Updated their profile", "bi-person-lines-fill", "Account"),
    RENAMED_BY_ADMIN("Name changed by an administrator", "bi-pencil", "Account"),
    EMAIL_CHANGED_BY_ADMIN("Email changed by an administrator", "bi-envelope", "Account"),
    ROLE_CHANGED("Role changed", "bi-person-gear", "Account"),
    ACCOUNT_DISABLED("Account disabled", "bi-person-x", "Account"),
    ACCOUNT_ENABLED("Account enabled", "bi-person-check", "Account"),
    USER_DELETED("Deleted a user account", "bi-trash", "Admin action");

    private final String label;
    private final String icon;
    private final String kind;

    ActivityType(String label, String icon, String kind) {
        this.label = label;
        this.icon = icon;
        this.kind = kind;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }

    public String getKind() {
        return kind;
    }
}
