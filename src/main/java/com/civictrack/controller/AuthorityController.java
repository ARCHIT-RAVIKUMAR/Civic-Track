package com.civictrack.controller;

import com.civictrack.dto.ComplaintFilter;
import com.civictrack.entity.Complaint;
import com.civictrack.entity.User;
import com.civictrack.enums.ComplaintStatus;
import com.civictrack.enums.Priority;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.service.CategoryService;
import com.civictrack.service.ComplaintService;
import com.civictrack.service.UserService;
import com.civictrack.service.WorkerStats;
import com.civictrack.service.WorkerStatsService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;

@Controller
@RequestMapping("/authority")
public class AuthorityController {

    private static final int PAGE_SIZE = 10;

    private final UserService userService;
    private final ComplaintService complaintService;
    private final CategoryService categoryService;
    private final ChartDataHelper chartDataHelper;
    private final WorkerStatsService workerStatsService;

    public AuthorityController(UserService userService, ComplaintService complaintService,
                               CategoryService categoryService, ChartDataHelper chartDataHelper,
                               WorkerStatsService workerStatsService) {
        this.userService = userService;
        this.complaintService = complaintService;
        this.categoryService = categoryService;
        this.chartDataHelper = chartDataHelper;
        this.workerStatsService = workerStatsService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@ModelAttribute("filter") ComplaintFilter filter,
                            @RequestParam(defaultValue = "0") int page,
                            Authentication auth, Model model) {
        User authority = userService.getByEmail(auth.getName());
        Page<Complaint> results = complaintService.search(filter, page, PAGE_SIZE, authority);
        model.addAttribute("page", results);
        model.addAttribute("statuses", ComplaintStatus.values());
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("counts", complaintService.countByStatus());
        model.addAttribute("overdueCount", complaintService.countOverdue());
        chartDataHelper.addTo(model);
        return "authority/dashboard";
    }

    /** Map of all complaints that have a pin, coloured by status. */
    @GetMapping("/map")
    public String map(@ModelAttribute("filter") ComplaintFilter filter, Authentication auth, Model model) {
        User authority = authority(auth);
        List<Map<String, Object>> pins = new ArrayList<>();
        for (Complaint c : complaintService.findForMap(filter, authority)) {
            Map<String, Object> pin = new LinkedHashMap<>();
            pin.put("id", c.getId().toString());
            pin.put("ref", c.getReferenceNumber());
            pin.put("title", c.getTitle());
            pin.put("status", c.getStatus().getLabel());
            pin.put("statusKey", c.getStatus().name());
            pin.put("overdue", c.isOverdue());
            pin.put("lat", c.getLatitude());
            pin.put("lng", c.getLongitude());
            pins.add(pin);
        }
        model.addAttribute("pins", pins);
        model.addAttribute("statuses", ComplaintStatus.values());
        model.addAttribute("categories", categoryService.findAll());
        return "authority/map";
    }

    /** OPTIONAL feature: CSV export of the filtered list. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@ModelAttribute ComplaintFilter filter, Authentication auth) {
        User authority = userService.getByEmail(auth.getName());
        String csv = complaintService.exportCsv(filter, authority);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"complaints.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/complaints/{id}/assign")
    public String assignPage(@PathVariable UUID id, Authentication auth, Model model) {
        User authority = userService.getByEmail(auth.getName());
        Complaint complaint = complaintService.getForViewer(id, authority);
        model.addAttribute("complaint", complaint);
        Map<java.util.UUID, Long> openJobs = workerStatsService.stats(authority).stream()
                .collect(Collectors.toMap(s -> s.getWorker().getId(), WorkerStats::getOpenJobs));
        List<User> workers = new ArrayList<>(userService.findActiveFieldWorkers());
        workers.sort(Comparator.comparingLong((User w) -> openJobs.getOrDefault(w.getId(), 0L))
                .thenComparing(User::getFullName));
        model.addAttribute("workers", workers);   // least busy first
        model.addAttribute("openJobs", openJobs);
        model.addAttribute("assignment", complaintService.getActiveAssignment(complaint).orElse(null));
        return "authority/assign";
    }

    @PostMapping("/complaints/{id}/assign")
    public String assign(@PathVariable UUID id, @RequestParam UUID workerId,
                         @RequestParam(required = false) String notes,
                         Authentication auth, RedirectAttributes redirect) {
        return run(id, redirect, "Complaint assigned.", authority(auth),
                a -> complaintService.assign(id, workerId, notes, a));
    }

    @PostMapping("/complaints/{id}/review")
    public String review(@PathVariable UUID id, Authentication auth, RedirectAttributes redirect) {
        return run(id, redirect, "Complaint moved to Under Review.", authority(auth),
                a -> complaintService.startReview(id, a));
    }

    @PostMapping("/complaints/{id}/reject")
    public String reject(@PathVariable UUID id, @RequestParam String reason,
                         Authentication auth, RedirectAttributes redirect) {
        return run(id, redirect, "Complaint rejected.", authority(auth),
                a -> complaintService.reject(id, reason, a));
    }

    @PostMapping("/complaints/{id}/priority")
    public String priority(@PathVariable UUID id, @RequestParam Priority priority,
                           Authentication auth, RedirectAttributes redirect) {
        return run(id, redirect, "Priority updated.", authority(auth),
                a -> complaintService.changePriority(id, priority, a));
    }

    @PostMapping("/complaints/{id}/close")
    public String close(@PathVariable UUID id, @RequestParam(required = false) String notes,
                        Authentication auth, RedirectAttributes redirect) {
        return run(id, redirect, "Complaint closed.", authority(auth),
                a -> complaintService.close(id, notes, a));
    }

    @PostMapping("/complaints/{id}/send-back")
    public String sendBack(@PathVariable UUID id, @RequestParam String notes,
                           Authentication auth, RedirectAttributes redirect) {
        return run(id, redirect, "Complaint sent back to the field worker.", authority(auth),
                a -> complaintService.sendBackToWork(id, notes, a));
    }

    private User authority(Authentication auth) {
        return userService.getByEmail(auth.getName());
    }

    /** Runs an action; shows a success or a friendly error message, then returns to the detail page. */
    private String run(UUID id, RedirectAttributes redirect, String successMessage, User actor,
                       java.util.function.Consumer<User> action) {
        try {
            action.accept(actor);
            redirect.addFlashAttribute("success", successMessage);
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/complaints/" + id;
    }
}
