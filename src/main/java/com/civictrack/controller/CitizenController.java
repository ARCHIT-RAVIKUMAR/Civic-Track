package com.civictrack.controller;

import com.civictrack.dto.ComplaintForm;
import com.civictrack.dto.FeedbackForm;
import com.civictrack.entity.Complaint;
import com.civictrack.entity.User;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.exception.ForbiddenOperationException;
import com.civictrack.service.CategoryService;
import com.civictrack.service.ComplaintService;
import com.civictrack.service.FeedbackService;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/citizen")
public class CitizenController {

    private final UserService userService;
    private final ComplaintService complaintService;
    private final CategoryService categoryService;
    private final FeedbackService feedbackService;

    public CitizenController(UserService userService, ComplaintService complaintService,
                             CategoryService categoryService, FeedbackService feedbackService) {
        this.userService = userService;
        this.complaintService = complaintService;
        this.categoryService = categoryService;
        this.feedbackService = feedbackService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication auth, Model model) {
        User citizen = userService.getByEmail(auth.getName());
        model.addAttribute("complaints", complaintService.findForCitizen(citizen));
        return "citizen/dashboard";
    }

    @GetMapping("/complaints/new")
    public String newComplaint(Model model) {
        model.addAttribute("complaintForm", new ComplaintForm());
        model.addAttribute("categories", categoryService.findActive());
        return "citizen/complaint-form";
    }

    @PostMapping("/complaints")
    public String submit(@Valid @ModelAttribute("complaintForm") ComplaintForm form,
                         BindingResult result,
                         @RequestParam(value = "image", required = false) MultipartFile image,
                         Authentication auth, Model model, RedirectAttributes redirect) {
        model.addAttribute("categories", categoryService.findActive());
        if (result.hasErrors()) {
            return "citizen/complaint-form";
        }
        User citizen = userService.getByEmail(auth.getName());
        try {
            Complaint saved = complaintService.submit(form, image, citizen);
            redirect.addFlashAttribute("success", "Complaint submitted. You can track it from your dashboard.");
            return "redirect:/complaints/" + saved.getId();
        } catch (BusinessRuleException ex) {
            model.addAttribute("error", ex.getMessage());
            return "citizen/complaint-form";
        }
    }

    @GetMapping("/complaints/{id}/feedback")
    public String feedbackForm(@PathVariable UUID id, Authentication auth, Model model) {
        User citizen = userService.getByEmail(auth.getName());
        Complaint complaint = complaintService.getForViewer(id, citizen);
        if (!feedbackService.canSubmitFeedback(complaint, citizen)) {
            throw new BusinessRuleException("Feedback is not available for this complaint.");
        }
        model.addAttribute("complaint", complaint);
        model.addAttribute("feedbackForm", new FeedbackForm());
        return "citizen/feedback";
    }

    @PostMapping("/complaints/{id}/feedback")
    public String submitFeedback(@PathVariable UUID id,
                                 @Valid @ModelAttribute("feedbackForm") FeedbackForm form,
                                 BindingResult result, Authentication auth,
                                 Model model, RedirectAttributes redirect) {
        User citizen = userService.getByEmail(auth.getName());
        Complaint complaint = complaintService.getForViewer(id, citizen);
        if (result.hasErrors()) {
            model.addAttribute("complaint", complaint);
            return "citizen/feedback";
        }
        try {
            feedbackService.submit(id, form, citizen);
        } catch (BusinessRuleException ex) {
            model.addAttribute("complaint", complaint);
            model.addAttribute("error", ex.getMessage());
            return "citizen/feedback";
        } catch (ForbiddenOperationException ex) {
            throw ex;
        }
        redirect.addFlashAttribute("success", "Thank you for your feedback!");
        return "redirect:/complaints/" + id;
    }
}
