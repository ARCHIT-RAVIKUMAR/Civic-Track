package com.civictrack.security;

import com.civictrack.entity.User;
import com.civictrack.enums.Role;
import com.civictrack.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityAccessTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void publicPagesAreOpen() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/login")).andExpect(status().isOk());
        mockMvc.perform(get("/register")).andExpect(status().isOk());
    }

    @Test
    void anonymousUsersAreSentToLogin() throws Exception {
        mockMvc.perform(get("/citizen/dashboard")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/authority/dashboard")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/complaints/" + UUID.randomUUID())).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void citizenCannotOpenAuthorityWorkerOrAdminAreas() throws Exception {
        mockMvc.perform(get("/authority/dashboard")).andExpect(status().isForbidden());
        mockMvc.perform(get("/worker/dashboard")).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "FIELD_WORKER")
    void workerCannotOpenCitizenArea() throws Exception {
        mockMvc.perform(get("/citizen/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void citizenCannotPostAuthorityActions() throws Exception {
        mockMvc.perform(post("/authority/complaints/" + UUID.randomUUID() + "/close").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "citizen@test.com", roles = "CITIZEN")
    void citizenSeesOwnDashboard() throws Exception {
        userRepository.save(new User("Test Citizen", "citizen@test.com", passwordEncoder.encode("Passw0rd1"), Role.CITIZEN));
        mockMvc.perform(get("/citizen/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("citizen/dashboard"));
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/register")
                        .param("fullName", "A").param("email", "a@b.com")
                        .param("password", "Passw0rd1").param("confirmPassword", "Passw0rd1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrationCreatesCitizenWithHashedPassword() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "New Citizen").param("email", "new@test.com")
                        .param("password", "Passw0rd1").param("confirmPassword", "Passw0rd1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        User saved = userRepository.findByEmail("new@test.com").orElseThrow();
        assertTrue(saved.hasRole(Role.CITIZEN));
        assertTrue(saved.getPasswordHash().startsWith("$2"), "password must be a BCrypt hash");
    }

    @Test
    void weakPasswordIsRejectedOnTheServer() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "Weak").param("email", "weak@test.com")
                        .param("password", "abc").param("confirmPassword", "abc"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeHasFieldErrors("registrationForm", "password"));
    }

    @Test
    void mismatchedPasswordsAreRejected() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "Mismatch").param("email", "mm@test.com")
                        .param("password", "Passw0rd1").param("confirmPassword", "Different1"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("registrationForm", "passwordConfirmed"));
    }

    @Test
    void loginSucceedsWithCorrectPasswordAndFailsWithWrongOne() throws Exception {
        userRepository.save(new User("Login User", "login@test.com", passwordEncoder.encode("Passw0rd1"), Role.CITIZEN));

        mockMvc.perform(formLogin("/login").user("login@test.com").password("Passw0rd1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));

        mockMvc.perform(formLogin("/login").user("login@test.com").password("wrong"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void disabledUserCannotLogIn() throws Exception {
        User u = new User("Off", "off@test.com", passwordEncoder.encode("Passw0rd1"), Role.CITIZEN);
        u.disable();
        userRepository.save(u);

        mockMvc.perform(formLogin("/login").user("off@test.com").password("Passw0rd1"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void logoutRedirectsToLogin() throws Exception {
        mockMvc.perform(logout("/logout"))
                .andExpect(redirectedUrl("/login?logout"));
    }

    // ---------------------------------------------------------------- new features

    @Test
    void accountIsLockedAfterRepeatedWrongPasswords() throws Exception {
        userRepository.save(new User("Lock Me", "lock@test.com", passwordEncoder.encode("Passw0rd1"), Role.CITIZEN));

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(formLogin("/login").user("lock@test.com").password("bad-password"))
                    .andExpect(redirectedUrl("/login?error"));
        }

        // even the CORRECT password is refused while the account is locked
        mockMvc.perform(formLogin("/login").user("lock@test.com").password("Passw0rd1"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void profileAndNotificationPagesNeedLogin() throws Exception {
        mockMvc.perform(get("/profile")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/notifications")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "profile@test.com", roles = "CITIZEN")
    void profileAndNotificationPagesLoadForLoggedInUser() throws Exception {
        userRepository.save(new User("Profile User", "profile@test.com", passwordEncoder.encode("Passw0rd1"), Role.CITIZEN));

        mockMvc.perform(get("/profile")).andExpect(status().isOk()).andExpect(view().name("profile/profile"));
        mockMvc.perform(get("/notifications")).andExpect(status().isOk()).andExpect(view().name("notifications/list"));
    }

    @Test
    @WithMockUser(username = "pw@test.com", roles = "CITIZEN")
    void userCanChangeOwnPassword() throws Exception {
        userRepository.save(new User("Pw User", "pw@test.com", passwordEncoder.encode("OldPassw0rd"), Role.CITIZEN));

        mockMvc.perform(post("/profile/password").with(csrf())
                        .param("currentPassword", "OldPassw0rd")
                        .param("newPassword", "NewPassw0rd2")
                        .param("confirmPassword", "NewPassw0rd2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));

        User saved = userRepository.findByEmail("pw@test.com").orElseThrow();
        assertTrue(passwordEncoder.matches("NewPassw0rd2", saved.getPasswordHash()));
    }

    @Test
    @WithMockUser(username = "pw2@test.com", roles = "CITIZEN")
    void changePasswordFailsWithWrongCurrentPassword() throws Exception {
        userRepository.save(new User("Pw User", "pw2@test.com", passwordEncoder.encode("OldPassw0rd"), Role.CITIZEN));

        mockMvc.perform(post("/profile/password").with(csrf())
                        .param("currentPassword", "WrongPass1")
                        .param("newPassword", "NewPassw0rd2")
                        .param("confirmPassword", "NewPassw0rd2"))
                .andExpect(status().isOk())
                .andExpect(view().name("profile/profile"));

        User saved = userRepository.findByEmail("pw2@test.com").orElseThrow();
        assertTrue(passwordEncoder.matches("OldPassw0rd", saved.getPasswordHash()));
    }
}
