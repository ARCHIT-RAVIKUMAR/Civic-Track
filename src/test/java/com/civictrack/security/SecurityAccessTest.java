package com.civictrack.security;

import com.civictrack.entity.Announcement;
import com.civictrack.entity.Category;
import com.civictrack.entity.Complaint;
import com.civictrack.entity.Notification;
import com.civictrack.entity.User;
import com.civictrack.enums.ActivityType;
import com.civictrack.repository.ActivityLogRepository;
import com.civictrack.enums.AnnouncementSeverity;
import com.civictrack.repository.AnnouncementRepository;
import com.civictrack.repository.CategoryRepository;
import com.civictrack.repository.ComplaintRepository;
import com.civictrack.repository.NotificationRepository;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
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
    @Autowired private ActivityLogRepository activityLogRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private AnnouncementRepository announcementRepository;

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

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminOverviewWithChartsLoads() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attributeExists("statusLabels", "categoryLabels"));
    }

    @Test
    @WithMockUser(username = "auditor@test.com", roles = "ADMIN")
    void adminAuditLogAndCategoriesLoad() throws Exception {
        userRepository.save(new User("Auditor", "auditor@test.com", passwordEncoder.encode("Passw0rd1"), Role.ADMIN));

        mockMvc.perform(get("/admin/audit")).andExpect(status().isOk()).andExpect(view().name("admin/audit"));
        mockMvc.perform(get("/admin/categories")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "AUTHORITY")
    void authorityCannotOpenAuditLog() throws Exception {
        mockMvc.perform(get("/admin/audit")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "officer@test.com", roles = "AUTHORITY")
    void authorityDashboardWithChartsAndSearchLoads() throws Exception {
        userRepository.save(new User("Officer", "officer@test.com", passwordEncoder.encode("Passw0rd1"), Role.AUTHORITY));

        mockMvc.perform(get("/authority/dashboard").param("q", "pothole"))
                .andExpect(status().isOk())
                .andExpect(view().name("authority/dashboard"));
    }

    @Test
    @WithMockUser(username = "mapper@test.com", roles = "CITIZEN")
    void complaintFormWithMapLoads() throws Exception {
        userRepository.save(new User("Mapper", "mapper@test.com", passwordEncoder.encode("Passw0rd1"), Role.CITIZEN));

        mockMvc.perform(get("/citizen/complaints/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("citizen/complaint-form"));
    }

    @Test
    void loginsAndFailedLoginsAreRecordedInTheActivityLog() throws Exception {
        User user = userRepository.save(new User("Tracked", "tracked@test.com", passwordEncoder.encode("Passw0rd1"), Role.CITIZEN));

        mockMvc.perform(formLogin("/login").user("tracked@test.com").password("wrong"));
        mockMvc.perform(formLogin("/login").user("tracked@test.com").password("Passw0rd1"));

        var types = activityLogRepository.findTop200ByUserOrderByCreatedAtDesc(user).stream()
                .map(a -> a.getType()).toList();
        assertTrue(types.contains(ActivityType.LOGIN_FAILED));
        assertTrue(types.contains(ActivityType.LOGIN_SUCCESS));
    }

    @Test
    @WithMockUser(username = "boss@test.com", roles = "ADMIN")
    void adminCanOpenAUsersActivityPage() throws Exception {
        userRepository.save(new User("Boss", "boss@test.com", passwordEncoder.encode("Passw0rd1"), Role.ADMIN));
        User subject = userRepository.save(new User("Subject", "subject@test.com", passwordEncoder.encode("Passw0rd1"), Role.CITIZEN));

        mockMvc.perform(get("/admin/users")).andExpect(status().isOk());
        mockMvc.perform(get("/admin/users/" + subject.getId() + "/activity"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/user-activity"));
    }

    @Test
    @WithMockUser(roles = "AUTHORITY")
    void nonAdminsCannotSeeAccountActivity() throws Exception {
        mockMvc.perform(get("/admin/users/" + UUID.randomUUID() + "/activity")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "worker2@test.com", roles = "FIELD_WORKER")
    void workerDashboardLoadsWithResponsiveTable() throws Exception {
        userRepository.save(new User("Worker Two", "worker2@test.com", passwordEncoder.encode("Passw0rd1"), Role.FIELD_WORKER));

        mockMvc.perform(get("/worker/dashboard")).andExpect(status().isOk()).andExpect(view().name("worker/dashboard"));
    }

    @Test
    @WithMockUser(username = "mapworker@test.com", roles = "FIELD_WORKER")
    void workerMapPageLoads() throws Exception {
        userRepository.save(new User("Map Worker", "mapworker@test.com", passwordEncoder.encode("Passw0rd1"), Role.FIELD_WORKER));

        mockMvc.perform(get("/worker/map")).andExpect(status().isOk()).andExpect(view().name("worker/map"));
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void citizenCannotOpenWorkerMap() throws Exception {
        mockMvc.perform(get("/worker/map")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "exporter@test.com", roles = "ADMIN")
    void adminCanDownloadCsvExports() throws Exception {
        userRepository.save(new User("Exporter", "exporter@test.com", passwordEncoder.encode("Passw0rd1"), Role.ADMIN));

        mockMvc.perform(get("/admin/users/export"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentTypeCompatibleWith("text/csv"));
        mockMvc.perform(get("/admin/audit/export")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "AUTHORITY")
    void authorityCannotDownloadAdminExports() throws Exception {
        mockMvc.perform(get("/admin/users/export")).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/audit/export")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "late@test.com", roles = "AUTHORITY")
    void authorityOverdueFilterLoads() throws Exception {
        userRepository.save(new User("Late Officer", "late@test.com", passwordEncoder.encode("Passw0rd1"), Role.AUTHORITY));

        mockMvc.perform(get("/authority/dashboard").param("overdue", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("overdueCount"));
    }

    @Test
    @WithMockUser(username = "resetter@test.com", roles = "ADMIN")
    void adminCanResetAnotherUsersPasswordAndTheyCanLogInWithIt() throws Exception {
        userRepository.save(new User("Resetter", "resetter@test.com", passwordEncoder.encode("Passw0rd1"), Role.ADMIN));
        User forgetful = userRepository.save(new User("Forgetful", "forgetful@test.com", passwordEncoder.encode("OldPassw0rd"), Role.CITIZEN));

        mockMvc.perform(get("/admin/users/" + forgetful.getId() + "/reset-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/reset-password"));

        mockMvc.perform(post("/admin/users/" + forgetful.getId() + "/reset-password").with(csrf())
                        .param("newPassword", "TempPass123")
                        .param("confirmPassword", "TempPass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        mockMvc.perform(formLogin("/login").user("forgetful@test.com").password("TempPass123"))
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void citizenCannotOpenThePasswordResetPage() throws Exception {
        mockMvc.perform(get("/admin/users/" + UUID.randomUUID() + "/reset-password")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "editor@test.com", roles = "ADMIN")
    void adminCanEditAUsersNameAndRoleFromTheEditPage() throws Exception {
        userRepository.save(new User("Editor", "editor@test.com", passwordEncoder.encode("Passw0rd1"), Role.ADMIN));
        User target = userRepository.save(new User("Old Name", "target@test.com", passwordEncoder.encode("Passw0rd1"), Role.CITIZEN));

        mockMvc.perform(get("/admin/users/" + target.getId() + "/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/user-edit"));

        mockMvc.perform(post("/admin/users/" + target.getId() + "/edit").with(csrf())
                        .param("fullName", "New Name")
                        .param("role", "FIELD_WORKER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        User saved = userRepository.findById(target.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("New Name", saved.getFullName());
        org.junit.jupiter.api.Assertions.assertEquals(Role.FIELD_WORKER, saved.getRole());
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void citizenCannotOpenTheUserEditPage() throws Exception {
        mockMvc.perform(get("/admin/users/" + UUID.randomUUID() + "/edit")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanOpenTheCategoryEditPage() throws Exception {
        Category category = categoryRepository.save(new Category("Test category", "Something"));

        mockMvc.perform(get("/admin/categories/" + category.getId() + "/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/category-edit"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminOverviewShowsEmptyStateWhenThereAreNoComplaints() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("chartHasData", false));
    }

    @Test
    @WithMockUser(username = "mailer@test.com", roles = "ADMIN")
    void adminCanChangeAUsersEmailAndTheyLogInWithTheNewOne() throws Exception {
        userRepository.save(new User("Mailer", "mailer@test.com", passwordEncoder.encode("Passw0rd1"), Role.ADMIN));
        User target = userRepository.save(new User("Typo Person", "typo@test.com", passwordEncoder.encode("Passw0rd1"), Role.CITIZEN));

        mockMvc.perform(post("/admin/users/" + target.getId() + "/edit").with(csrf())
                        .param("fullName", "Typo Person")
                        .param("email", "fixed@test.com")
                        .param("role", "CITIZEN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        mockMvc.perform(formLogin("/login").user("fixed@test.com").password("Passw0rd1"))
                .andExpect(redirectedUrl("/dashboard"));
        mockMvc.perform(formLogin("/login").user("typo@test.com").password("Passw0rd1"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @WithMockUser(username = "dup@test.com", roles = "ADMIN")
    void duplicateEmailKeepsTheAdminOnTheEditForm() throws Exception {
        userRepository.save(new User("Dup Admin", "dup@test.com", passwordEncoder.encode("Passw0rd1"), Role.ADMIN));
        userRepository.save(new User("Existing", "existing@test.com", passwordEncoder.encode("Passw0rd1"), Role.CITIZEN));
        User target = userRepository.save(new User("Other", "other@test.com", passwordEncoder.encode("Passw0rd1"), Role.CITIZEN));

        mockMvc.perform(post("/admin/users/" + target.getId() + "/edit").with(csrf())
                        .param("fullName", "Other")
                        .param("email", "existing@test.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users/" + target.getId() + "/edit"));
    }

    // ---------------------------------------------------------------- v9 features

    @Test
    void publicPagesAndAppFilesAreReachableWithoutLogin() throws Exception {
        mockMvc.perform(get("/help")).andExpect(status().isOk());
        mockMvc.perform(get("/track")).andExpect(status().isOk());
        mockMvc.perform(get("/health")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("OK"));
        mockMvc.perform(get("/manifest.webmanifest")).andExpect(status().isOk());
        mockMvc.perform(get("/sw.js")).andExpect(status().isOk());
        mockMvc.perform(get("/offline.html")).andExpect(status().isOk());
        mockMvc.perform(get("/icons/icon-192.png")).andExpect(status().isOk());
    }

    @Test
    void publicTrackingShowsStatusButNothingPrivate() throws Exception {
        User owner = userRepository.save(new User("Private Person", "private@test.com", passwordEncoder.encode("Passw0rd1"), Role.CITIZEN));
        Category category = categoryRepository.save(new Category("Tracking category", null));
        Complaint complaint = complaintRepository.save(new Complaint("Secret title words", "Secret description text here",
                "Secret Street 5", category, owner));

        mockMvc.perform(get("/track").param("ref", complaint.getReferenceNumber().toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(view().name("track"))
                .andExpect(model().attributeExists("info"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString(complaint.getReferenceNumber())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Secret title words"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Private Person"))));

        mockMvc.perform(get("/track").param("ref", "CT-ZZZZ-ZZZZ"))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("info"));
    }

    @Test
    void activeAnnouncementIsShownOnTheLandingPage() throws Exception {
        announcementRepository.save(new Announcement("Water supply off in Ward 5", "Tomorrow 9 to 5.",
                AnnouncementSeverity.WARNING, null, null));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Water supply off in Ward 5")));
    }

    @Test
    @WithMockUser(username = "announcer@test.com", roles = "ADMIN")
    void adminCanPublishAndSwitchOffAnnouncements() throws Exception {
        userRepository.save(new User("Announcer", "announcer@test.com", passwordEncoder.encode("Passw0rd1"), Role.ADMIN));

        mockMvc.perform(get("/admin/announcements")).andExpect(status().isOk())
                .andExpect(view().name("admin/announcements"));

        mockMvc.perform(post("/admin/announcements").with(csrf())
                        .param("title", "Office closed")
                        .param("message", "Closed on Monday.")
                        .param("severity", "INFO"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/announcements"));

        org.junit.jupiter.api.Assertions.assertEquals(1, announcementRepository.findAll().size());
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void citizenCannotManageAnnouncementsOrSeeWorkerPerformance() throws Exception {
        mockMvc.perform(get("/admin/announcements")).andExpect(status().isForbidden());
        mockMvc.perform(get("/workers/performance")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "boss2@test.com", roles = "AUTHORITY")
    void authorityCanOpenWorkerPerformanceAndTheComplaintsMap() throws Exception {
        userRepository.save(new User("Boss Two", "boss2@test.com", passwordEncoder.encode("Passw0rd1"), Role.AUTHORITY));
        userRepository.save(new User("A Worker", "aworker@test.com", passwordEncoder.encode("Passw0rd1"), Role.FIELD_WORKER));

        mockMvc.perform(get("/workers/performance")).andExpect(status().isOk())
                .andExpect(view().name("workers/performance"));
        mockMvc.perform(get("/authority/map")).andExpect(status().isOk())
                .andExpect(view().name("authority/map"));
    }

    @Test
    @WithMockUser(username = "adminperf@test.com", roles = "ADMIN")
    void adminCanAlsoSeeWorkerPerformance() throws Exception {
        userRepository.save(new User("Admin Perf", "adminperf@test.com", passwordEncoder.encode("Passw0rd1"), Role.ADMIN));

        mockMvc.perform(get("/workers/performance")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "phone@test.com", roles = "CITIZEN")
    void bottomNavigationBarIsShownOnlyToLoggedInUsers() throws Exception {
        userRepository.save(new User("Phone User", "phone@test.com", passwordEncoder.encode("Passw0rd1"), Role.CITIZEN));

        mockMvc.perform(get("/citizen/dashboard"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("aria-label=\"Quick navigation\"")));
    }

    @Test
    void anonymousVisitorsDoNotGetTheBottomNavigationBar() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("aria-label=\"Quick navigation\""))));
    }

    @Test
    @WithMockUser(username = "poller@test.com", roles = "CITIZEN")
    void unreadCountEndpointReturnsJsonForTheLoggedInUser() throws Exception {
        userRepository.save(new User("Poller", "poller@test.com", passwordEncoder.encode("Passw0rd1"), Role.CITIZEN));

        mockMvc.perform(get("/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith("application/json"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.count").value(0));
    }

    @Test
    void unreadCountEndpointNeedsLogin() throws Exception {
        mockMvc.perform(get("/notifications/unread-count")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "namer@test.com", roles = "CITIZEN")
    void navbarShowsTheFullNameAndARefreshButtonIsOnTheDashboard() throws Exception {
        userRepository.save(new User("Asha Kumar Full Name", "namer@test.com", passwordEncoder.encode("Passw0rd1"), Role.CITIZEN));

        mockMvc.perform(get("/citizen/dashboard"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Asha Kumar Full Name")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("data-refresh")));
    }

    @Test
    @WithMockUser(username = "mystats@test.com", roles = "FIELD_WORKER")
    void workerCanOpenTheirOwnStatsPage() throws Exception {
        userRepository.save(new User("Stats Worker", "mystats@test.com", passwordEncoder.encode("Passw0rd1"), Role.FIELD_WORKER));

        mockMvc.perform(get("/worker/stats")).andExpect(status().isOk()).andExpect(view().name("worker/stats"));
    }

    @Test
    @WithMockUser(roles = "AUTHORITY")
    void authorityCannotOpenTheWorkerStatsPage() throws Exception {
        mockMvc.perform(get("/worker/stats")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void satisfactionSummaryIsOnTheAdminOverview() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("satisfaction"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Citizen satisfaction")));
    }

    @Test
    @WithMockUser(username = "newbie@test.com", roles = "CITIZEN")
    void newUsersSeeTheWelcomeGuideUntilTheyDismissIt() throws Exception {
        User newbie = userRepository.save(new User("Newbie", "newbie@test.com", passwordEncoder.encode("Passw0rd1"), Role.CITIZEN));

        mockMvc.perform(get("/citizen/dashboard"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("id=\"welcomeModal\"")));

        mockMvc.perform(post("/profile/welcome-done").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
        assertTrue(userRepository.findById(newbie.getId()).orElseThrow().isWelcomeSeen());

        mockMvc.perform(get("/citizen/dashboard"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("id=\"welcomeModal\""))));

        mockMvc.perform(post("/profile/welcome-reset").with(csrf())).andExpect(status().is3xxRedirection());
        assertFalse(userRepository.findById(newbie.getId()).orElseThrow().isWelcomeSeen());
    }

    @Test
    @WithMockUser(username = "reader@test.com", roles = "CITIZEN")
    void notificationCanBeMarkedReadWithoutOpeningIt() throws Exception {
        User reader = userRepository.save(new User("Reader", "reader@test.com", passwordEncoder.encode("Passw0rd1"), Role.CITIZEN));
        Notification n = notificationRepository.save(new Notification(reader, null, "Hello", "Something happened"));
        assertFalse(n.isRead());

        mockMvc.perform(post("/notifications/" + n.getId() + "/read").with(csrf()).header("X-Requested-With", "fetch"))
                .andExpect(status().isNoContent());

        assertTrue(notificationRepository.findById(n.getId()).orElseThrow().isRead());
    }

    @Test
    @WithMockUser(username = "thief@test.com", roles = "CITIZEN")
    void nobodyCanMarkSomeoneElsesNotificationRead() throws Exception {
        userRepository.save(new User("Thief", "thief@test.com", passwordEncoder.encode("Passw0rd1"), Role.CITIZEN));
        User owner = userRepository.save(new User("Owner", "owner@test.com", passwordEncoder.encode("Passw0rd1"), Role.CITIZEN));
        Notification n = notificationRepository.save(new Notification(owner, null, "Private", "For the owner only"));

        mockMvc.perform(post("/notifications/" + n.getId() + "/read").with(csrf()).header("X-Requested-With", "fetch"))
                .andExpect(status().isForbidden());
        assertFalse(notificationRepository.findById(n.getId()).orElseThrow().isRead());
    }

    @Test
    @WithMockUser(username = "plain@test.com", roles = "CITIZEN")
    void markReadWithoutJavascriptRedirectsBackToTheList() throws Exception {
        User plain = userRepository.save(new User("Plain", "plain@test.com", passwordEncoder.encode("Passw0rd1"), Role.CITIZEN));
        Notification n = notificationRepository.save(new Notification(plain, null, "Hi", "Message"));

        mockMvc.perform(post("/notifications/" + n.getId() + "/read").with(csrf()))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/notifications"));
    }
}
