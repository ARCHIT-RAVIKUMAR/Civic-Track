package com.civictrack.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "index";
    }

    /** Sends each logged-in user to the dashboard that matches their role. */
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication) {
        for (var authority : authentication.getAuthorities()) {
            switch (authority.getAuthority()) {
                case "ROLE_CITIZEN":
                    return "redirect:/citizen/dashboard";
                case "ROLE_AUTHORITY":
                    return "redirect:/authority/dashboard";
                case "ROLE_FIELD_WORKER":
                    return "redirect:/worker/dashboard";
                case "ROLE_ADMIN":
                    return "redirect:/admin/dashboard";
                default:
                    break;
            }
        }
        return "redirect:/";
    }

    @GetMapping("/access-denied")
    public String accessDenied(Model model) {
        model.addAttribute("status", 403);
        model.addAttribute("message", "You do not have permission to open this page.");
        return "error";
    }
}
