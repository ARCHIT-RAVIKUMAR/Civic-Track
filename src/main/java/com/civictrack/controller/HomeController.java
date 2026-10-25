package com.civictrack.controller;

import com.civictrack.enums.ComplaintStatus;
import com.civictrack.service.ComplaintService;
import com.civictrack.service.TrackingInfo;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;
import java.util.Optional;

@Controller
public class HomeController {

    private final ComplaintService complaintService;

    public HomeController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @GetMapping("/")
    public String home(Model model) {
        Map<ComplaintStatus, Long> counts = complaintService.countByStatus();
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long resolved = counts.getOrDefault(ComplaintStatus.RESOLVED, 0L)
                + counts.getOrDefault(ComplaintStatus.CLOSED, 0L);
        long inProgress = counts.getOrDefault(ComplaintStatus.IN_PROGRESS, 0L)
                + counts.getOrDefault(ComplaintStatus.ASSIGNED, 0L);
        model.addAttribute("totalReported", total);
        model.addAttribute("totalResolved", resolved);
        model.addAttribute("totalInProgress", inProgress);
        return "index";
    }

    /** Public: anyone can look up the status of a complaint by its reference number. */
    @GetMapping("/track")
    public String track(@RequestParam(required = false) String ref, Model model) {
        if (ref != null && !ref.isBlank()) {
            Optional<TrackingInfo> info = complaintService.track(ref);
            model.addAttribute("ref", ref.trim());
            model.addAttribute("searched", true);
            model.addAttribute("info", info.orElse(null));
        }
        return "track";
    }

    @GetMapping("/help")
    public String help() {
        return "help";
    }

    /** Simple health check for the hosting platform. */
    @GetMapping("/health")
    @ResponseBody
    public String health() {
        return "OK";
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
