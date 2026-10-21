package com.civictrack.controller;

import com.civictrack.dto.ChangePasswordForm;
import com.civictrack.entity.User;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** "My profile": every logged-in user can edit their own name and change their password. */
@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String profile(Authentication auth, Model model) {
        model.addAttribute("profileUser", userService.getByEmail(auth.getName()));
        model.addAttribute("passwordForm", new ChangePasswordForm());
        return "profile/profile";
    }

    @PostMapping("/name")
    public String updateName(@RequestParam String fullName, Authentication auth, RedirectAttributes redirect) {
        User user = userService.getByEmail(auth.getName());
        try {
            userService.updateOwnName(user, fullName);
            redirect.addFlashAttribute("success", "Your name was updated.");
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/password")
    public String changePassword(@Valid @ModelAttribute("passwordForm") ChangePasswordForm form,
                                 BindingResult result, Authentication auth,
                                 Model model, RedirectAttributes redirect) {
        User user = userService.getByEmail(auth.getName());
        model.addAttribute("profileUser", user);
        if (result.hasErrors()) {
            return "profile/profile";
        }
        try {
            userService.changePassword(user, form.getCurrentPassword(), form.getNewPassword());
        } catch (BusinessRuleException ex) {
            model.addAttribute("error", ex.getMessage());
            return "profile/profile";
        }
        redirect.addFlashAttribute("success", "Your password was changed.");
        return "redirect:/profile";
    }
}
