package com.civictrack.service;

import com.civictrack.dto.AdminUserForm;
import com.civictrack.dto.RegistrationForm;
import com.civictrack.entity.User;
import com.civictrack.enums.Role;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.exception.ForbiddenOperationException;
import com.civictrack.repository.AssignmentRepository;
import com.civictrack.repository.ComplaintCommentRepository;
import com.civictrack.repository.ComplaintRepository;
import com.civictrack.repository.FeedbackRepository;
import com.civictrack.repository.NotificationRepository;
import com.civictrack.repository.StatusHistoryRepository;
import com.civictrack.repository.UserRepository;
import com.civictrack.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ComplaintRepository complaintRepository;
    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private StatusHistoryRepository historyRepository;
    @Mock
    private FeedbackRepository feedbackRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private ComplaintCommentRepository commentRepository;
    @Mock
    private ActivityService activityService;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, passwordEncoder, complaintRepository,
                assignmentRepository, historyRepository, feedbackRepository, notificationRepository, commentRepository,
                activityService);
    }

    private RegistrationForm form(String email) {
        RegistrationForm f = new RegistrationForm();
        f.setFullName("Asha Kumar");
        f.setEmail(email);
        f.setPassword("Passw0rd1");
        f.setConfirmPassword("Passw0rd1");
        return f;
    }

    @Test
    void registerCreatesCitizenWithHashedPassword() {
        when(userRepository.existsByEmail("asha@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd1")).thenReturn("HASHED");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = service.register(form("  Asha@Example.com "));

        assertEquals(Role.CITIZEN, saved.getRole());
        assertEquals("asha@example.com", saved.getEmail());
        assertEquals("HASHED", saved.getPasswordHash());
        assertNotEquals("Passw0rd1", saved.getPasswordHash());
    }

    @Test
    void duplicateEmailIsRejected() {
        when(userRepository.existsByEmail("asha@example.com")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.register(form("asha@example.com")));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void onlyAdminsCanCreateStaffAccounts() {
        User citizen = new User("C", "c@example.com", "h", Role.CITIZEN);
        AdminUserForm adminForm = new AdminUserForm();
        adminForm.setFullName("Worker");
        adminForm.setEmail("w@example.com");
        adminForm.setPassword("Passw0rd1");
        adminForm.setRole(Role.FIELD_WORKER);

        assertThrows(ForbiddenOperationException.class, () -> service.createByAdmin(adminForm, citizen));
    }

    private static User userWithId(String name, String email, Role role) {
        User u = new User(name, email, "hash", role);
        ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
        return u;
    }

    @Test
    void adminCanRenameUser() {
        User admin = userWithId("Admin", "admin@example.com", Role.ADMIN);
        User target = userWithId("Old Name", "t@example.com", Role.CITIZEN);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        service.updateName(target.getId(), "  New Name ", admin);

        assertEquals("New Name", target.getFullName());
        verify(userRepository).save(target);
    }

    @Test
    void nonAdminCannotRenameUsers() {
        User citizen = userWithId("C", "c@example.com", Role.CITIZEN);
        assertThrows(ForbiddenOperationException.class,
                () -> service.updateName(UUID.randomUUID(), "Whatever", citizen));
    }

    @Test
    void blankNameIsRejected() {
        User admin = userWithId("Admin", "admin@example.com", Role.ADMIN);
        assertThrows(BusinessRuleException.class,
                () -> service.updateName(UUID.randomUUID(), "   ", admin));
    }

    @Test
    void adminCanDeleteUserWithNoActivity() {
        User admin = userWithId("Admin", "admin@example.com", Role.ADMIN);
        User target = userWithId("Unused", "u@example.com", Role.CITIZEN);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        service.deleteUser(target.getId(), admin);

        verify(notificationRepository).deleteByRecipient(target);
        verify(activityService).forget(target);
        verify(userRepository).delete(target);
    }

    @Test
    void userWithComplaintHistoryCannotBeDeleted() {
        User admin = userWithId("Admin", "admin@example.com", Role.ADMIN);
        User target = userWithId("Active", "a@example.com", Role.CITIZEN);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(complaintRepository.existsByCitizen(target)).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.deleteUser(target.getId(), admin));
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void adminCannotDeleteOwnAccount() {
        User admin = userWithId("Admin", "admin@example.com", Role.ADMIN);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThrows(BusinessRuleException.class, () -> service.deleteUser(admin.getId(), admin));
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void nonAdminCannotDeleteUsers() {
        User citizen = userWithId("C", "c@example.com", Role.CITIZEN);
        assertThrows(ForbiddenOperationException.class,
                () -> service.deleteUser(UUID.randomUUID(), citizen));
    }

    @Test
    void userCanChangePasswordWithCorrectCurrentPassword() {
        User user = userWithId("Asha", "asha@example.com", Role.CITIZEN);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPassw0rd", "hash")).thenReturn(true);
        when(passwordEncoder.matches("NewPassw0rd", "hash")).thenReturn(false);
        when(passwordEncoder.encode("NewPassw0rd")).thenReturn("NEWHASH");

        service.changePassword(user, "OldPassw0rd", "NewPassw0rd");

        assertEquals("NEWHASH", user.getPasswordHash());
        verify(userRepository).save(user);
    }

    @Test
    void wrongCurrentPasswordIsRejected() {
        User user = userWithId("Asha", "asha@example.com", Role.CITIZEN);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Wrong1234", "hash")).thenReturn(false);

        assertThrows(BusinessRuleException.class, () -> service.changePassword(user, "Wrong1234", "NewPassw0rd"));
        assertEquals("hash", user.getPasswordHash());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void userCanRenameThemselves() {
        User user = userWithId("Old", "o@example.com", Role.CITIZEN);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        service.updateOwnName(user, "  Fresh Name ");

        assertEquals("Fresh Name", user.getFullName());
    }

    @Test
    void userWhoWroteCommentsCannotBeDeleted() {
        User admin = userWithId("Admin", "admin@example.com", Role.ADMIN);
        User target = userWithId("Talker", "t@example.com", Role.AUTHORITY);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(commentRepository.existsByAuthor(target)).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.deleteUser(target.getId(), admin));
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void registrationAndPasswordChangeAreRecordedInTheActivityLog() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd1")).thenReturn("HASHED");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User created = service.register(form("new@example.com"));

        verify(activityService).record(created, com.civictrack.enums.ActivityType.ACCOUNT_REGISTERED,
                "Self-registered as a citizen", null);
    }

    @Test
    void roleChangeIsRecordedWithTheAdminAsActor() {
        User admin = userWithId("Admin", "admin@example.com", Role.ADMIN);
        User target = userWithId("Worker", "w@example.com", Role.CITIZEN);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        service.changeRole(target.getId(), Role.FIELD_WORKER, admin);

        verify(activityService).record(any(User.class), eq(com.civictrack.enums.ActivityType.ROLE_CHANGED),
                anyString(), eq(admin));
    }

    @Test
    void userCsvIsAdminOnlyAndNeverContainsPasswords() {
        User admin = userWithId("Admin", "admin@example.com", Role.ADMIN);
        User citizen = userWithId("=Evil", "c@example.com", Role.CITIZEN);
        when(userRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(citizen));

        String csv = service.exportCsv(admin);

        assertTrue(csv.startsWith("Name,Email,Role,Status,Created"));
        assertTrue(csv.contains("c@example.com"));
        assertTrue(csv.contains("\"'=Evil\""), "formula-like names must be neutralised");
        assertFalse(csv.contains("hash"), "password hashes must never be exported");
        assertThrows(ForbiddenOperationException.class, () -> service.exportCsv(citizen));
    }

    @Test
    void adminCanResetAnotherUsersPassword() {
        User admin = userWithId("Admin", "admin@example.com", Role.ADMIN);
        User target = userWithId("Forgetful", "f@example.com", Role.CITIZEN);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(passwordEncoder.encode("TempPass123")).thenReturn("TEMPHASH");

        service.resetPassword(target.getId(), "TempPass123", admin);

        assertEquals("TEMPHASH", target.getPasswordHash());
        verify(activityService).record(target, com.civictrack.enums.ActivityType.PASSWORD_RESET_BY_ADMIN, null, admin);
    }

    @Test
    void adminCannotResetOwnPasswordThisWay() {
        User admin = userWithId("Admin", "admin@example.com", Role.ADMIN);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThrows(BusinessRuleException.class, () -> service.resetPassword(admin.getId(), "TempPass123", admin));
        assertEquals("hash", admin.getPasswordHash());
    }

    @Test
    void shortResetPasswordIsRejectedAndNonAdminsAreRefused() {
        User admin = userWithId("Admin", "admin@example.com", Role.ADMIN);
        User target = userWithId("Forgetful", "f@example.com", Role.CITIZEN);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThrows(BusinessRuleException.class, () -> service.resetPassword(target.getId(), "short", admin));
        assertThrows(ForbiddenOperationException.class, () -> service.resetPassword(target.getId(), "TempPass123", target));
    }

    @Test
    void adminCanEditNameEmailAndRoleTogether() {
        User admin = userWithId("Admin", "admin@example.com", Role.ADMIN);
        User target = userWithId("Old Name", "old@example.com", Role.CITIZEN);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

        service.updateDetails(target.getId(), "  New Name ", " New@Example.com ", Role.FIELD_WORKER, admin);

        assertEquals("New Name", target.getFullName());
        assertEquals("new@example.com", target.getEmail());
        assertEquals(Role.FIELD_WORKER, target.getRole());
        verify(activityService).record(any(User.class), eq(com.civictrack.enums.ActivityType.EMAIL_CHANGED_BY_ADMIN),
                anyString(), eq(admin));
    }

    @Test
    void emailAlreadyUsedByAnotherAccountIsRejectedAndNothingChanges() {
        User admin = userWithId("Admin", "admin@example.com", Role.ADMIN);
        User target = userWithId("Old Name", "old@example.com", Role.CITIZEN);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThrows(BusinessRuleException.class,
                () -> service.updateDetails(target.getId(), "Changed", "taken@example.com", Role.AUTHORITY, admin));

        assertEquals("Old Name", target.getFullName());
        assertEquals("old@example.com", target.getEmail());
        assertEquals(Role.CITIZEN, target.getRole());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void invalidEmailIsRejected() {
        User admin = userWithId("Admin", "admin@example.com", Role.ADMIN);
        User target = userWithId("Name", "old@example.com", Role.CITIZEN);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThrows(BusinessRuleException.class,
                () -> service.updateDetails(target.getId(), "Name", "not-an-email", null, admin));
    }

    @Test
    void adminCannotChangeOwnEmailOrRoleButCanRenameThemselves() {
        User admin = userWithId("Admin", "admin@example.com", Role.ADMIN);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThrows(BusinessRuleException.class,
                () -> service.updateDetails(admin.getId(), "Admin", "other@example.com", null, admin));
        assertThrows(BusinessRuleException.class,
                () -> service.updateDetails(admin.getId(), "Admin", null, Role.CITIZEN, admin));

        service.updateDetails(admin.getId(), "Chief Admin", null, null, admin);
        assertEquals("Chief Admin", admin.getFullName());
        assertEquals("admin@example.com", admin.getEmail());
    }

    @Test
    void nullEmailAndRoleMeanUnchanged() {
        User admin = userWithId("Admin", "admin@example.com", Role.ADMIN);
        User target = userWithId("Name", "keep@example.com", Role.AUTHORITY);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        service.updateDetails(target.getId(), "Name", null, null, admin);

        assertEquals("keep@example.com", target.getEmail());
        assertEquals(Role.AUTHORITY, target.getRole());
    }

    @Test
    void welcomeGuideCanBeDismissedAndShownAgain() {
        User user = userWithId("New", "new@example.com", Role.CITIZEN);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        assertFalse(user.isWelcomeSeen());

        service.setWelcomeSeen(user, true);
        assertTrue(user.isWelcomeSeen());

        service.setWelcomeSeen(user, false);
        assertFalse(user.isWelcomeSeen());
    }
}
