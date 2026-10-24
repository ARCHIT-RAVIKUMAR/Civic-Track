package com.civictrack.controller;

import com.civictrack.entity.Complaint;
import com.civictrack.entity.User;
import com.civictrack.enums.Role;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.service.ComplaintService;
import com.civictrack.service.FeedbackService;
import com.civictrack.service.UserService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/** Pages shared by all roles. Access is decided inside ComplaintService.getForViewer(). */
@Controller
public class ComplaintController {

    private final UserService userService;
    private final ComplaintService complaintService;
    private final FeedbackService feedbackService;

    public ComplaintController(UserService userService, ComplaintService complaintService,
                               FeedbackService feedbackService) {
        this.userService = userService;
        this.complaintService = complaintService;
        this.feedbackService = feedbackService;
    }

    @GetMapping("/complaints/{id}")
    public String detail(@PathVariable UUID id, Authentication auth, Model model) {
        User viewer = userService.getByEmail(auth.getName());
        Complaint complaint = complaintService.getForViewer(id, viewer);
        model.addAttribute("complaint", complaint);
        model.addAttribute("history", complaintService.getHistory(complaint));
        model.addAttribute("assignment", complaintService.getActiveAssignment(complaint).orElse(null));
        model.addAttribute("feedback", feedbackService.findByComplaint(complaint).orElse(null));
        model.addAttribute("canFeedback", feedbackService.canSubmitFeedback(complaint, viewer));
        model.addAttribute("viewerRole", viewer.getRole().name());
        model.addAttribute("comments", complaintService.getComments(complaint));
        model.addAttribute("canReopen", complaintService.canReopen(complaint, viewer));
        model.addAttribute("canComment", viewer.getRole() != Role.ADMIN && !complaint.getStatus().isTerminal());
        return "complaint/detail";
    }

    @PostMapping("/complaints/{id}/comments")
    public String addComment(@PathVariable UUID id, @RequestParam String message,
                             Authentication auth, RedirectAttributes redirect) {
        User viewer = userService.getByEmail(auth.getName());
        try {
            complaintService.addComment(id, message, viewer);
            redirect.addFlashAttribute("success", "Comment added.");
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/complaints/" + id;
    }

    @GetMapping("/complaints/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable UUID id, Authentication auth) {
        User viewer = userService.getByEmail(auth.getName());
        Complaint complaint = complaintService.getForViewer(id, viewer);
        Resource image = complaintService.loadImage(complaint);
        MediaType type = complaint.getImageFilename().endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.noStore())
                .body(image);
    }
}
