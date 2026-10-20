package com.civictrack.service;

import com.civictrack.dto.AdminUserForm;
import com.civictrack.dto.RegistrationForm;
import com.civictrack.entity.User;
import com.civictrack.enums.Role;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.exception.ForbiddenOperationException;
import com.civictrack.repository.UserRepository;
import com.civictrack.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, passwordEncoder);
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
}
