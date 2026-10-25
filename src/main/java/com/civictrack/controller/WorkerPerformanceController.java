package com.civictrack.controller;

import com.civictrack.entity.User;
import com.civictrack.service.UserService;
import com.civictrack.service.WorkerStatsService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Workload and results of every field worker. Open to authorities and administrators. */
@Controller
public class WorkerPerformanceController {

    private final UserService userService;
    private final WorkerStatsService workerStatsService;

    public WorkerPerformanceController(UserService userService, WorkerStatsService workerStatsService) {
        this.userService = userService;
        this.workerStatsService = workerStatsService;
    }

    @GetMapping("/workers/performance")
    public String performance(Authentication auth, Model model) {
        User viewer = userService.getByEmail(auth.getName());
        model.addAttribute("stats", workerStatsService.stats(viewer));
        return "workers/performance";
    }
}
