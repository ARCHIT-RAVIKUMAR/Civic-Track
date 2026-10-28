package com.civictrack.entity;

import com.civictrack.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "welcome_seen", nullable = false)
    private boolean welcomeSeen = false;

    protected User() {
        // required by JPA
    }

    public User(String fullName, String email, String passwordHash, Role role) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash is required");
        }
        if (role == null) {
            throw new IllegalArgumentException("Role is required");
        }
        this.fullName = fullName.trim();
        this.email = email.trim().toLowerCase();
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isWelcomeSeen() {
        return welcomeSeen;
    }

    public void markWelcomeSeen() {
        this.welcomeSeen = true;
    }

    public void resetWelcome() {
        this.welcomeSeen = false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void changeRole(Role newRole) {
        if (newRole == null) {
            throw new IllegalArgumentException("Role is required");
        }
        this.role = newRole;
    }

    public void changePasswordHash(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash is required");
        }
        this.passwordHash = newPasswordHash;
    }

    public void changeEmail(String newEmail) {
        if (newEmail == null || newEmail.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        this.email = newEmail.trim().toLowerCase();
    }

    public void rename(String newFullName) {
        if (newFullName == null || newFullName.isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }
        this.fullName = newFullName.trim();
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public boolean hasRole(Role r) {
        return this.role == r;
    }
}
