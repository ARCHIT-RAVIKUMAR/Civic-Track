package com.civictrack.controller;

import com.civictrack.entity.User;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.service.ComplaintService;
import com.civictrack.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;
import java.util.function.Consumer;

@Controller
@RequestMapping("/worker")
public class WorkerController {

    private final UserService userService;
    private final ComplaintService complaintService;

    public WorkerController(UserService userService, ComplaintService complaintService) {
        this.userService = userService;
        this.complaintService = complaintService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication auth, Model model) {
        User worker = userService.getByEmail(auth.getName());
        model.addAttribute("complaints", complaintService.findForWorker(worker));
        return "worker/dashboard";
    }

    @PostMapping("/complaints/{id}/start")
    public String start(@PathVariable UUID id, Authentication auth, RedirectAttributes redirect) {
        return run(id, redirect, "Work started.", auth, w -> complaintService.startWork(id, w));
    }

    @PostMapping("/complaints/{id}/note")
    public String note(@PathVariable UUID id, @RequestParam String note,
                       Authentication auth, RedirectAttributes redirect) {
        return run(id, redirect, "Progress note added.", auth, w -> complaintService.addNote(id, note, w));
    }

    @PostMapping("/complaints/{id}/resolve")
    public String resolve(@PathVariable UUID id, @RequestParam(required = false) String note,
                          Authentication auth, RedirectAttributes redirect) {
        return run(id, redirect, "Marked as resolved. The authority will verify it.", auth,
                w -> complaintService.markResolved(id, note, w));
    }

    private String run(UUID id, RedirectAttributes redirect, String successMessage,
                       Authentication auth, Consumer<User> action) {
        User worker = userService.getByEmail(auth.getName());
        try {
            action.accept(worker);
            redirect.addFlashAttribute("success", successMessage);
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/complaints/" + id;
    }
}
