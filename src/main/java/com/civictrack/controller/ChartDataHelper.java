package com.civictrack.controller;

import com.civictrack.enums.ComplaintStatus;
import com.civictrack.service.ComplaintService;
import com.civictrack.service.SatisfactionService;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Prepares the label/value lists that the dashboard charts need (shared by admin and authority pages). */
@Component
public class ChartDataHelper {

    private final ComplaintService complaintService;
    private final SatisfactionService satisfactionService;

    public ChartDataHelper(ComplaintService complaintService, SatisfactionService satisfactionService) {
        this.complaintService = complaintService;
        this.satisfactionService = satisfactionService;
    }

    public void addTo(Model model) {
        Map<ComplaintStatus, Long> counts = complaintService.countByStatus();
        List<String> statusLabels = new ArrayList<>();
        List<Long> statusValues = new ArrayList<>();
        long total = 0;
        for (ComplaintStatus status : ComplaintStatus.values()) {
            if (status == ComplaintStatus.REJECTED || status == ComplaintStatus.WITHDRAWN) {
                continue; // shown together below, so the chart stays readable
            }
            long value = counts.getOrDefault(status, 0L);
            statusLabels.add(status.getLabel());
            statusValues.add(value);
            total += value;
        }
        long notPursued = counts.getOrDefault(ComplaintStatus.REJECTED, 0L)
                + counts.getOrDefault(ComplaintStatus.WITHDRAWN, 0L);
        statusLabels.add("Rejected / withdrawn");
        statusValues.add(notPursued);
        total += notPursued;

        List<String> categoryLabels = new ArrayList<>();
        List<Long> categoryValues = new ArrayList<>();
        for (Map.Entry<String, Long> entry : complaintService.countByCategory().entrySet()) {
            categoryLabels.add(entry.getKey());
            categoryValues.add(entry.getValue());
        }
        model.addAttribute("statusLabels", statusLabels);
        model.addAttribute("statusValues", statusValues);
        model.addAttribute("categoryLabels", categoryLabels);
        model.addAttribute("categoryValues", categoryValues);
        model.addAttribute("chartHasData", total > 0);
        model.addAttribute("satisfaction", satisfactionService.summary());
    }
}
