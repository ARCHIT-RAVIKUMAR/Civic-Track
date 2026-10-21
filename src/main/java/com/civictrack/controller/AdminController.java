package com.civictrack.controller;

import com.civictrack.dto.AdminUserForm;
import com.civictrack.dto.CategoryForm;
import com.civictrack.entity.User;
import com.civictrack.enums.Role;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.service.CategoryService;
import com.civictrack.service.ComplaintService;
import com.civictrack.service.UserService;
import jakarta.validation.Valid;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserService userService;
    private final CategoryService categoryService;
    private final ComplaintService complaintService;

    public AdminController(UserService userService, CategoryService categoryService,
                           ComplaintService complaintService) {
        this.userService = userService;
        this.categoryService = categoryService;
        this.complaintService = complaintService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Map<String, Long> usersByRole = new LinkedHashMap<>();
        for (Role role : Role.values()) {
            usersByRole.put(role.getLabel(), userService.countByRole(role));
        }
        model.addAttribute("usersByRole", usersByRole);
        model.addAttribute("counts", complaintService.countByStatus());
        model.addAttribute("recent", complaintService.findRecent());
        return "admin/dashboard";
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
