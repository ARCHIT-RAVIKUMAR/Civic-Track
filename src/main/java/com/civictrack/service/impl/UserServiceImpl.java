package com.civictrack.service.impl;

import com.civictrack.dto.AdminUserForm;
import com.civictrack.dto.RegistrationForm;
import com.civictrack.entity.User;
import com.civictrack.enums.ActivityType;
import com.civictrack.enums.Role;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.exception.ForbiddenOperationException;
import com.civictrack.exception.ResourceNotFoundException;
import com.civictrack.repository.AssignmentRepository;
import com.civictrack.repository.ComplaintCommentRepository;
import com.civictrack.repository.ComplaintRepository;
import com.civictrack.repository.FeedbackRepository;
import com.civictrack.repository.NotificationRepository;
import com.civictrack.repository.StatusHistoryRepository;
import com.civictrack.repository.UserRepository;
import com.civictrack.service.ActivityService;
import com.civictrack.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** COMPOSITION: built from a repository and a password encoder it is given (constructor injection). */
@Service
@Transactional
public class UserServiceImpl implements UserService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ComplaintRepository complaintRepository;
    private final AssignmentRepository assignmentRepository;
    private final StatusHistoryRepository historyRepository;
    private final FeedbackRepository feedbackRepository;
    private final NotificationRepository notificationRepository;
    private final ComplaintCommentRepository commentRepository;
    private final ActivityService activityService;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           ComplaintRepository complaintRepository, AssignmentRepository assignmentRepository,
                           StatusHistoryRepository historyRepository, FeedbackRepository feedbackRepository,
                           NotificationRepository notificationRepository,
                           ComplaintCommentRepository commentRepository,
                           ActivityService activityService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.complaintRepository = complaintRepository;
        this.assignmentRepository = assignmentRepository;
        this.historyRepository = historyRepository;
        this.feedbackRepository = feedbackRepository;
        this.notificationRepository = notificationRepository;
        this.commentRepository = commentRepository;
        this.activityService = activityService;
    }

    @Override
    public User register(RegistrationForm form) {
        User created = create(form.getFullName(), form.getEmail(), form.getPassword(), Role.CITIZEN);
        activityService.record(created, ActivityType.ACCOUNT_REGISTERED, "Self-registered as a citizen", null);
        return created;
    }

    @Override
    public User createByAdmin(AdminUserForm form, User admin) {
        requireAdmin(admin);
        User created = create(form.getFullName(), form.getEmail(), form.getPassword(), form.getRole());
        activityService.record(created, ActivityType.ACCOUNT_CREATED_BY_ADMIN,
                "Created with role " + form.getRole().getLabel(), admin);
        return created;
    }

    private User create(String fullName, String email, String rawPassword, Role role) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessRuleException("An account with this email already exists.");
        }
        String hash = passwordEncoder.encode(rawPassword); // never store the raw password
        return userRepository.save(new User(fullName, normalizedEmail, hash, role));
    }

    @Override
    @Transactional(readOnly = true)
    public User getByEmail(String email) {
        return userRepository.findByEmail(email == null ? "" : email.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    @Override
    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findActiveFieldWorkers() {
        return findActiveByRole(Role.FIELD_WORKER);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findActiveByRole(Role role) {
        return userRepository.findByRoleOrderByFullNameAsc(role).stream()
                .filter(User::isEnabled)
                .toList();
    }

    @Override
    public void changePassword(User user, String currentPassword, String newPassword) {
        User target = getById(user.getId());
        if (!passwordEncoder.matches(currentPassword, target.getPasswordHash())) {
            throw new BusinessRuleException("Your current password is incorrect.");
        }
        if (passwordEncoder.matches(newPassword, target.getPasswordHash())) {
            throw new BusinessRuleException("Choose a password that is different from the current one.");
        }
        target.changePasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(target);
        activityService.record(target, ActivityType.PASSWORD_CHANGED, null, null);
    }

    @Override
    public void updateOwnName(User user, String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new BusinessRuleException("Name cannot be empty.");
        }
        if (fullName.trim().length() > 100) {
            throw new BusinessRuleException("Name is too long (maximum 100 characters).");
        }
        User target = getById(user.getId());
        String oldName = target.getFullName();
        target.rename(fullName);
        userRepository.save(target);
        activityService.record(target, ActivityType.PROFILE_UPDATED,
                "Name changed from '" + oldName + "' to '" + target.getFullName() + "'", null);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByRole(Role role) {
        return userRepository.countByRole(role);
    }

    @Override
    public void changeRole(UUID userId, Role newRole, User admin) {
        requireAdmin(admin);
        User target = getById(userId);
        if (target.getId().equals(admin.getId())) {
            throw new BusinessRuleException("You cannot change your own role.");
        }
        Role oldRole = target.getRole();
        target.changeRole(newRole);
        userRepository.save(target);
        activityService.record(target, ActivityType.ROLE_CHANGED,
                "From " + oldRole.getLabel() + " to " + newRole.getLabel(), admin);
    }

    @Override
    public void toggleEnabled(UUID userId, User admin) {
        requireAdmin(admin);
        User target = getById(userId);
        if (target.getId().equals(admin.getId())) {
            throw new BusinessRuleException("You cannot disable your own account.");
        }
        boolean wasEnabled = target.isEnabled();
        if (wasEnabled) {
            target.disable();
        } else {
            target.enable();
        }
        userRepository.save(target);
        activityService.record(target, wasEnabled ? ActivityType.ACCOUNT_DISABLED : ActivityType.ACCOUNT_ENABLED,
                null, admin);
    }

    @Override
    public void updateDetails(UUID userId, String fullName, String email, Role role, User admin) {
        requireAdmin(admin);
        User target = getById(userId);
        boolean self = target.getId().equals(admin.getId());

        // 1) validate everything first, so a bad value never leaves a half-saved account
        String newName = fullName == null ? "" : fullName.trim();
        if (newName.isEmpty()) {
            throw new BusinessRuleException("Name cannot be empty.");
        }
        if (newName.length() > 100) {
            throw new BusinessRuleException("Name is too long (maximum 100 characters).");
        }
        String newEmail = (email == null || email.isBlank()) ? target.getEmail() : email.trim().toLowerCase();
        boolean emailChanged = !newEmail.equals(target.getEmail());
        if (emailChanged) {
            if (self) {
                throw new BusinessRuleException("You cannot change your own email here. Ask another administrator.");
            }
            if (newEmail.length() > 150 || !EMAIL_PATTERN.matcher(newEmail).matches()) {
                throw new BusinessRuleException("Enter a valid email address.");
            }
            if (userRepository.existsByEmail(newEmail)) {
                throw new BusinessRuleException("Another account already uses this email.");
            }
        }
        Role newRole = role == null ? target.getRole() : role;
        boolean roleChanged = newRole != target.getRole();
        if (roleChanged && self) {
            throw new BusinessRuleException("You cannot change your own role.");
        }

        // 2) apply and record
        if (!newName.equals(target.getFullName())) {
            String oldName = target.getFullName();
            target.rename(newName);
            activityService.record(target, ActivityType.RENAMED_BY_ADMIN,
                    "From '" + oldName + "' to '" + newName + "'", admin);
        }
        if (emailChanged) {
            String oldEmail = target.getEmail();
            target.changeEmail(newEmail);
            activityService.record(target, ActivityType.EMAIL_CHANGED_BY_ADMIN,
                    "From " + oldEmail + " to " + newEmail, admin);
        }
        if (roleChanged) {
            Role oldRole = target.getRole();
            target.changeRole(newRole);
            activityService.record(target, ActivityType.ROLE_CHANGED,
                    "From " + oldRole.getLabel() + " to " + newRole.getLabel(), admin);
        }
        userRepository.save(target);
    }

    @Override
    public void resetPassword(UUID userId, String newPassword, User admin) {
        requireAdmin(admin);
        User target = getById(userId);
        if (target.getId().equals(admin.getId())) {
            throw new BusinessRuleException("Use My profile to change your own password.");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new BusinessRuleException("The new password must be at least 8 characters.");
        }
        target.changePasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(target);
        activityService.record(target, ActivityType.PASSWORD_RESET_BY_ADMIN, null, admin);
    }

    @Override
    public void updateName(UUID userId, String fullName, User admin) {
        requireAdmin(admin);
        if (fullName == null || fullName.isBlank()) {
            throw new BusinessRuleException("Name cannot be empty.");
        }
        if (fullName.trim().length() > 100) {
            throw new BusinessRuleException("Name is too long (maximum 100 characters).");
        }
        User target = getById(userId);
        String oldName = target.getFullName();
        target.rename(fullName);
        userRepository.save(target);
        activityService.record(target, ActivityType.RENAMED_BY_ADMIN,
                "From '" + oldName + "' to '" + target.getFullName() + "'", admin);
    }

    @Override
    @Transactional(readOnly = true)
    public String exportCsv(User admin) {
        requireAdmin(admin);
        StringBuilder csv = new StringBuilder("Name,Email,Role,Status,Created\n");
        for (User u : userRepository.findAllByOrderByCreatedAtDesc()) {
            csv.append(CsvUtil.row(u.getFullName(), u.getEmail(), u.getRole().getLabel(),
                    u.isEnabled() ? "Active" : "Disabled", u.getCreatedAtDisplay()));
        }
        return csv.toString();
    }

    @Override
    public void deleteUser(UUID userId, User admin) {
        requireAdmin(admin);
        User target = getById(userId);
        if (target.getId().equals(admin.getId())) {
            throw new BusinessRuleException("You cannot delete your own account.");
        }
        boolean hasActivity = complaintRepository.existsByCitizen(target)
                || assignmentRepository.existsByWorker(target)
                || assignmentRepository.existsByAssignedBy(target)
                || historyRepository.existsByChangedBy(target)
                || feedbackRepository.existsByCitizen(target)
                || commentRepository.existsByAuthor(target);
        if (hasActivity) {
            throw new BusinessRuleException(
                    "This user has complaint history, so it cannot be deleted. Disable the account instead.");
        }
        activityService.record(admin, ActivityType.USER_DELETED,
                "Deleted " + target.getRole().getLabel() + " account " + target.getEmail(), admin);
        notificationRepository.deleteByRecipient(target); // their inbox goes with them
        activityService.forget(target);                   // ... and their account history
        userRepository.delete(target);
    }

    private void requireAdmin(User actor) {
        if (actor == null || !actor.hasRole(Role.ADMIN)) {
            throw new ForbiddenOperationException("Only administrators can do this.");
        }
    }
}
