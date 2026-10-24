package com.civictrack.service;

import com.civictrack.dto.AdminUserForm;
import com.civictrack.dto.RegistrationForm;
import com.civictrack.entity.User;
import com.civictrack.enums.Role;

import java.util.List;
import java.util.UUID;

public interface UserService {
    /** Public self-registration. Always creates a CITIZEN. */
    User register(RegistrationForm form);

    User createByAdmin(AdminUserForm form, User admin);

    User getByEmail(String email);

    User getById(UUID id);

    List<User> findAll();

    List<User> findActiveFieldWorkers();

    List<User> findActiveByRole(Role role);

    void changePassword(User user, String currentPassword, String newPassword);

    void updateOwnName(User user, String fullName);

    long countByRole(Role role);

    void changeRole(UUID userId, Role newRole, User admin);

    void toggleEnabled(UUID userId, User admin);

    /** Admin sets a new password for someone who forgot theirs. Not allowed on your own account. */
    void resetPassword(UUID userId, String newPassword, User admin);

    void updateName(UUID userId, String fullName, User admin);

    /**
     * Admin edits a person's name, login email and role in one step. A null email or role means "leave unchanged".
     * Nothing is changed unless everything is valid. Admins cannot change their own email or role.
     */
    void updateDetails(UUID userId, String fullName, String email, Role role, User admin);

    /** CSV list of all accounts (admin only). Never includes passwords. */
    String exportCsv(User admin);

    /** Deletes an account that has no activity. Accounts with history must be disabled instead. */
    void deleteUser(UUID userId, User admin);
}
