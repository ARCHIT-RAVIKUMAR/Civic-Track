package com.civictrack.service;

import com.civictrack.dto.AdminUserForm;
import com.civictrack.dto.RegistrationForm;
import com.civictrack.entity.User;
import com.civictrack.enums.Role;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.exception.ForbiddenOperationException;
import com.civictrack.repository.AssignmentRepository;
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

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, passwordEncoder, complaintRepository,
                assignmentRepository, historyRepository, feedbackRepository, notificationRepository);
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
}
