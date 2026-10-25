package com.civictrack.controller;

import com.civictrack.dto.AdminUserForm;
import com.civictrack.dto.AnnouncementForm;
import com.civictrack.dto.CategoryForm;
import com.civictrack.dto.ResetPasswordForm;
import com.civictrack.entity.StatusHistory;
import com.civictrack.entity.User;
import com.civictrack.enums.ComplaintStatus;
import com.civictrack.enums.Role;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.service.ActivityItem;
import com.civictrack.service.ActivityService;
import com.civictrack.service.AnnouncementService;
import com.civictrack.service.CategoryService;
import com.civictrack.service.ComplaintService;
import com.civictrack.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserService userService;
    private final CategoryService categoryService;
    private final ComplaintService complaintService;
    private final ChartDataHelper chartDataHelper;
    private final ActivityService activityService;
    private final AnnouncementService announcementService;

    public AdminController(UserService userService, CategoryService categoryService,
                           ComplaintService complaintService, ChartDataHelper chartDataHelper,
                           ActivityService activityService, AnnouncementService announcementService) {
        this.userService = userService;
        this.categoryService = categoryService;
        this.complaintService = complaintService;
        this.chartDataHelper = chartDataHelper;
        this.activityService = activityService;
        this.announcementService = announcementService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Map<String, Long> usersByRole = new LinkedHashMap<>();
        for (Role role : Role.values()) {
            usersByRole.put(role.getLabel(), userService.countByRole(role));
        }
        model.addAttribute("usersByRole", usersByRole);
        Map<ComplaintStatus, Long> counts = complaintService.countByStatus();
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long open = counts.getOrDefault(ComplaintStatus.SUBMITTED, 0L)
                + counts.getOrDefault(ComplaintStatus.UNDER_REVIEW, 0L)
                + counts.getOrDefault(ComplaintStatus.ASSIGNED, 0L)
                + counts.getOrDefault(ComplaintStatus.IN_PROGRESS, 0L);
        model.addAttribute("counts", counts);
        model.addAttribute("totalComplaints", total);
        model.addAttribute("openComplaints", open);
        model.addAttribute("awaitingVerification", counts.getOrDefault(ComplaintStatus.RESOLVED, 0L));
        model.addAttribute("closedComplaints", counts.getOrDefault(ComplaintStatus.CLOSED, 0L));
        model.addAttribute("totalUsers", usersByRole.values().stream().mapToLong(Long::longValue).sum());
        model.addAttribute("recent", complaintService.findRecent());
        chartDataHelper.addTo(model);
        return "admin/dashboard";
    }

    // ---------------- audit log ----------------

    @GetMapping("/audit/export")
    public ResponseEntity<byte[]> exportAudit(Authentication auth) {
        return csv(complaintService.exportAuditCsv(admin(auth)), "audit-log.csv");
    }

    @GetMapping("/users/export")
    public ResponseEntity<byte[]> exportUsers(Authentication auth) {
        return csv(userService.exportCsv(admin(auth)), "users.csv");
    }

    private ResponseEntity<byte[]> csv(String content, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(content.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/audit")
    public String audit(@RequestParam(defaultValue = "0") int page, Authentication auth, Model model) {
        Page<StatusHistory> entries = complaintService.findAuditLog(page, 25, admin(auth));
        model.addAttribute("page", entries);
        return "admin/audit";
    }

    // ---------------- users ----------------

    @GetMapping("/users")
    public String users(Model model) {
        populateUsersPage(model);
        model.addAttribute("userForm", new AdminUserForm());
        return "admin/users";
    }

    @PostMapping("/users")
    public String createUser(@Valid @ModelAttribute("userForm") AdminUserForm form, BindingResult result,
                             Authentication auth, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            populateUsersPage(model);
            return "admin/users";
        }
        try {
            userService.createByAdmin(form, admin(auth));
        } catch (BusinessRuleException ex) {
            result.rejectValue("email", "duplicate", ex.getMessage());
            populateUsersPage(model);
            return "admin/users";
        }
        redirect.addFlashAttribute("success", "User created.");
        return "redirect:/admin/users";
    }

    @GetMapping("/users/{id}/activity")
    public String userActivity(@PathVariable UUID id, Authentication auth, Model model) {
        User adminUser = admin(auth);
        User subject = userService.getById(id);
        List<ActivityItem> items = activityService.timeline(id, adminUser);
        model.addAttribute("subject", subject);
        model.addAttribute("items", items);
        model.addAttribute("byKind", items.stream().collect(
                Collectors.groupingBy(ActivityItem::getKind, LinkedHashMap::new, Collectors.counting())));
        model.addAttribute("lastLogin", activityService.lastLogin(subject).orElse(null));
        return "admin/user-activity";
    }

    @GetMapping("/users/{id}/reset-password")
    public String resetPasswordForm(@PathVariable UUID id, Model model) {
        model.addAttribute("subject", userService.getById(id));
        model.addAttribute("resetForm", new ResetPasswordForm());
        return "admin/reset-password";
    }

    @PostMapping("/users/{id}/reset-password")
    public String resetPassword(@PathVariable UUID id,
                                @Valid @ModelAttribute("resetForm") ResetPasswordForm form,
                                BindingResult result, Authentication auth,
                                Model model, RedirectAttributes redirect) {
        User subject = userService.getById(id);
        model.addAttribute("subject", subject);
        if (result.hasErrors()) {
            return "admin/reset-password";
        }
        try {
            userService.resetPassword(id, form.getNewPassword(), admin(auth));
        } catch (BusinessRuleException ex) {
            model.addAttribute("error", ex.getMessage());
            return "admin/reset-password";
        }
        redirect.addFlashAttribute("success", "Password reset for " + subject.getFullName()
                + ". Ask them to change it from My profile after logging in.");
        return "redirect:/admin/users";
    }

    @GetMapping("/users/{id}/edit")
    public String editUserForm(@PathVariable UUID id, Authentication auth, Model model) {
        User subject = userService.getById(id);
        model.addAttribute("subject", subject);
        model.addAttribute("self", subject.getId().equals(admin(auth).getId()));
        model.addAttribute("roles", Role.values());
        return "admin/user-edit";
    }

    @PostMapping("/users/{id}/edit")
    public String editUser(@PathVariable UUID id, @RequestParam String fullName,
                           @RequestParam(required = false) String email,
                           @RequestParam(required = false) Role role,
                           Authentication auth, RedirectAttributes redirect) {
        try {
            userService.updateDetails(id, fullName, email, role, admin(auth));
            redirect.addFlashAttribute("success", "User updated.");
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/users/" + id + "/edit"; // stay on the form so it can be fixed
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/role")
    public String changeRole(@PathVariable UUID id, @RequestParam Role role,
                             Authentication auth, RedirectAttributes redirect) {
        try {
            userService.changeRole(id, role, admin(auth));
            redirect.addFlashAttribute("success", "Role updated. The user must log in again for it to apply.");
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/name")
    public String renameUser(@PathVariable UUID id, @RequestParam String fullName,
                             Authentication auth, RedirectAttributes redirect) {
        try {
            userService.updateName(id, fullName, admin(auth));
            redirect.addFlashAttribute("success", "Name updated.");
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable UUID id, Authentication auth, RedirectAttributes redirect) {
        try {
            userService.deleteUser(id, admin(auth));
            redirect.addFlashAttribute("success", "User deleted.");
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/toggle")
    public String toggleUser(@PathVariable UUID id, Authentication auth, RedirectAttributes redirect) {
        try {
            userService.toggleEnabled(id, admin(auth));
            redirect.addFlashAttribute("success", "User status updated.");
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ---------------- announcements ----------------

    @GetMapping("/announcements")
    public String announcements(Authentication auth, Model model) {
        model.addAttribute("announcementList", announcementService.findAll(admin(auth)));
        model.addAttribute("announcementForm", new AnnouncementForm());
        model.addAttribute("severities", com.civictrack.enums.AnnouncementSeverity.values());
        return "admin/announcements";
    }

    @PostMapping("/announcements")
    public String createAnnouncement(@Valid @ModelAttribute("announcementForm") AnnouncementForm form,
                                     BindingResult result, Authentication auth,
                                     Model model, RedirectAttributes redirect) {
        User adminUser = admin(auth);
        if (!result.hasErrors()) {
            try {
                announcementService.create(form, adminUser);
                redirect.addFlashAttribute("success", "Announcement published.");
                return "redirect:/admin/announcements";
            } catch (BusinessRuleException ex) {
                model.addAttribute("error", ex.getMessage());
            }
        }
        model.addAttribute("announcementList", announcementService.findAll(adminUser));
        model.addAttribute("severities", com.civictrack.enums.AnnouncementSeverity.values());
        return "admin/announcements";
    }

    @PostMapping("/announcements/{id}/toggle")
    public String toggleAnnouncement(@PathVariable UUID id, Authentication auth, RedirectAttributes redirect) {
        announcementService.toggle(id, admin(auth));
        redirect.addFlashAttribute("success", "Announcement updated.");
        return "redirect:/admin/announcements";
    }

    @PostMapping("/announcements/{id}/delete")
    public String deleteAnnouncement(@PathVariable UUID id, Authentication auth, RedirectAttributes redirect) {
        announcementService.delete(id, admin(auth));
        redirect.addFlashAttribute("success", "Announcement deleted.");
        return "redirect:/admin/announcements";
    }

    // ---------------- categories ----------------

    @GetMapping("/categories")
    public String categories(Model model) {
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("categoryForm", new CategoryForm());
        return "admin/categories";
    }

    @PostMapping("/categories")
    public String createCategory(@Valid @ModelAttribute("categoryForm") CategoryForm form, BindingResult result,
                                 Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("categories", categoryService.findAll());
            return "admin/categories";
        }
        try {
            categoryService.create(form);
        } catch (BusinessRuleException ex) {
            result.rejectValue("name", "duplicate", ex.getMessage());
            model.addAttribute("categories", categoryService.findAll());
            return "admin/categories";
        }
        redirect.addFlashAttribute("success", "Category added.");
        return "redirect:/admin/categories";
    }

    @GetMapping("/categories/{id}/edit")
    public String editCategoryForm(@PathVariable UUID id, Model model) {
        model.addAttribute("category", categoryService.getById(id));
        return "admin/category-edit";
    }

    @PostMapping("/categories/{id}/edit")
    public String editCategory(@PathVariable UUID id, @RequestParam String name,
                               @RequestParam(required = false) String description,
                               RedirectAttributes redirect) {
        try {
            categoryService.update(id, name, description);
            redirect.addFlashAttribute("success", "Category updated.");
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/{id}/toggle")
    public String toggleCategory(@PathVariable UUID id, RedirectAttributes redirect) {
        categoryService.toggleActive(id);
        redirect.addFlashAttribute("success", "Category updated.");
        return "redirect:/admin/categories";
    }

    private void populateUsersPage(Model model) {
        model.addAttribute("users", userService.findAll());
        model.addAttribute("roles", Role.values());
    }

    private User admin(Authentication auth) {
        return userService.getByEmail(auth.getName());
    }
}
