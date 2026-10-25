package com.civictrack.service.impl;

import com.civictrack.entity.Complaint;
import com.civictrack.entity.Feedback;
import com.civictrack.entity.StatusHistory;
import com.civictrack.entity.User;
import com.civictrack.enums.ComplaintStatus;
import com.civictrack.enums.Role;
import com.civictrack.exception.ForbiddenOperationException;
import com.civictrack.repository.AssignmentRepository;
import com.civictrack.repository.FeedbackRepository;
import com.civictrack.repository.StatusHistoryRepository;
import com.civictrack.repository.UserRepository;
import com.civictrack.service.WorkerStats;
import com.civictrack.service.WorkerStatsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class WorkerStatsServiceImpl implements WorkerStatsService {

    private final UserRepository userRepository;
    private final AssignmentRepository assignmentRepository;
    private final StatusHistoryRepository historyRepository;
    private final FeedbackRepository feedbackRepository;

    public WorkerStatsServiceImpl(UserRepository userRepository, AssignmentRepository assignmentRepository,
                                  StatusHistoryRepository historyRepository, FeedbackRepository feedbackRepository) {
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.historyRepository = historyRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @Override
    public List<WorkerStats> stats(User actor) {
        if (actor == null || !(actor.hasRole(Role.AUTHORITY) || actor.hasRole(Role.ADMIN))) {
            throw new ForbiddenOperationException("Only authorities and administrators can see worker performance.");
        }
        List<WorkerStats> result = new ArrayList<>();
        for (User worker : userRepository.findByRoleOrderByFullNameAsc(Role.FIELD_WORKER)) {
            result.add(statsFor(worker));
        }
        return result;
    }

    private WorkerStats statsFor(User worker) {
        long open = 0;
        long overdue = 0;
        for (Complaint c : assignmentRepository.findComplaintsForWorker(worker)) {
            if (c.getStatus() == ComplaintStatus.ASSIGNED || c.getStatus() == ComplaintStatus.IN_PROGRESS) {
                open++;
                if (c.isOverdue()) {
                    overdue++;
                }
            }
        }

        // the latest "Resolved" entry this worker made for each complaint
        Map<UUID, StatusHistory> completions = new LinkedHashMap<>();
        for (StatusHistory h : historyRepository
                .findTop500ByChangedByAndNewStatusOrderByCreatedAtDesc(worker, ComplaintStatus.RESOLVED)) {
            completions.putIfAbsent(h.getComplaint().getId(), h);
        }

        double totalMinutes = 0;
        int timed = 0;
        double ratingSum = 0;
        int ratings = 0;
        for (StatusHistory done : completions.values()) {
            Complaint complaint = done.getComplaint();
            LocalDateTime assignedAt = null;
            for (StatusHistory h : historyRepository.findByComplaintOrderByCreatedAtAsc(complaint)) {
                if (h.getNewStatus() == ComplaintStatus.ASSIGNED && h.getCreatedAt() != null
                        && done.getCreatedAt() != null && h.getCreatedAt().isBefore(done.getCreatedAt())) {
                    assignedAt = h.getCreatedAt(); // the most recent assignment before completion
                }
            }
            if (assignedAt != null) {
                totalMinutes += Duration.between(assignedAt, done.getCreatedAt()).toMinutes();
                timed++;
            }
            Optional<Feedback> feedback = feedbackRepository.findByComplaint(complaint);
            if (feedback.isPresent()) {
                ratingSum += feedback.get().getRating();
                ratings++;
            }
        }
        return new WorkerStats(worker, open, overdue, completions.size(),
                timed == 0 ? null : totalMinutes / timed,
                ratings == 0 ? null : ratingSum / ratings,
                ratings);
    }
}
