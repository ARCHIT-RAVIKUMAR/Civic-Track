package com.civictrack.service.impl;

import com.civictrack.entity.ActivityLog;
import com.civictrack.entity.Complaint;
import com.civictrack.entity.ComplaintComment;
import com.civictrack.entity.Feedback;
import com.civictrack.entity.StatusHistory;
import com.civictrack.entity.User;
import com.civictrack.enums.ActivityType;
import com.civictrack.enums.Role;
import com.civictrack.exception.ForbiddenOperationException;
import com.civictrack.exception.ResourceNotFoundException;
import com.civictrack.repository.ActivityLogRepository;
import com.civictrack.repository.ComplaintCommentRepository;
import com.civictrack.repository.ComplaintRepository;
import com.civictrack.repository.FeedbackRepository;
import com.civictrack.repository.StatusHistoryRepository;
import com.civictrack.repository.UserRepository;
import com.civictrack.service.ActivityItem;
import com.civictrack.service.ActivityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class ActivityServiceImpl implements ActivityService {

    private static final int LIMIT = 200;

    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;
    private final ComplaintRepository complaintRepository;
    private final StatusHistoryRepository historyRepository;
    private final ComplaintCommentRepository commentRepository;
    private final FeedbackRepository feedbackRepository;

    public ActivityServiceImpl(ActivityLogRepository activityLogRepository, UserRepository userRepository,
                               ComplaintRepository complaintRepository, StatusHistoryRepository historyRepository,
                               ComplaintCommentRepository commentRepository, FeedbackRepository feedbackRepository) {
        this.activityLogRepository = activityLogRepository;
        this.userRepository = userRepository;
        this.complaintRepository = complaintRepository;
        this.historyRepository = historyRepository;
        this.commentRepository = commentRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @Override
    public void record(User user, ActivityType type, String details, User actor) {
        activityLogRepository.save(new ActivityLog(user, type, details, actor));
    }

    @Override
    public void recordLoginEvent(String email, ActivityType type) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        userRepository.findByEmail(normalized).ifPresent(user -> record(user, type, null, null));
    }

    @Override
    public void forget(User user) {
        activityLogRepository.deleteByUser(user);
        activityLogRepository.clearActor(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActivityLog> lastLogin(User user) {
        return activityLogRepository.findFirstByUserAndTypeOrderByCreatedAtDesc(user, ActivityType.LOGIN_SUCCESS);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityItem> timeline(UUID userId, User admin) {
        if (admin == null || !admin.hasRole(Role.ADMIN)) {
            throw new ForbiddenOperationException("Only administrators can view account activity.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        List<ActivityItem> items = new ArrayList<>();

        for (ActivityLog log : activityLogRepository.findTop200ByUserOrderByCreatedAtDesc(user)) {
            String text = log.getType().getLabel();
            if (log.getDetails() != null) {
                text += ": " + log.getDetails();
            }
            if (log.getActor() != null && !log.getActor().equals(user)) {
                text += " (by " + log.getActor().getFullName() + ")";
            }
            items.add(new ActivityItem(log.getCreatedAt(), log.getType().getKind(), log.getType().getIcon(), text, null));
        }

        for (Complaint c : complaintRepository.findByCitizenOrderByCreatedAtDesc(user)) {
            items.add(new ActivityItem(c.getCreatedAt(), "Complaint", "bi-megaphone",
                    "Submitted complaint '" + c.getTitle() + "'", c.getId()));
        }

        for (StatusHistory h : historyRepository.findTop200ByChangedByOrderByCreatedAtDesc(user)) {
            if (h.getOldStatus() == null) {
                continue; // the "submitted" entry is already shown as the complaint itself
            }
            Complaint c = h.getComplaint();
            String text;
            if (h.getOldStatus() == h.getNewStatus()) {
                text = "Update on '" + c.getTitle() + "'" + (h.getNotes() != null ? ": " + h.getNotes() : "");
            } else {
                text = "Moved '" + c.getTitle() + "' from " + h.getOldStatus().getLabel()
                        + " to " + h.getNewStatus().getLabel() + (h.getNotes() != null ? " - " + h.getNotes() : "");
            }
            items.add(new ActivityItem(h.getCreatedAt(), "Work", "bi-arrow-repeat", text, c.getId()));
        }

        for (ComplaintComment cm : commentRepository.findTop200ByAuthorOrderByCreatedAtDesc(user)) {
            Complaint c = cm.getComplaint();
            items.add(new ActivityItem(cm.getCreatedAt(), "Comment", "bi-chat-left-text",
                    "Commented on '" + c.getTitle() + "': " + shorten(cm.getMessage()), c.getId()));
        }

        for (Feedback f : feedbackRepository.findTop200ByCitizenOrderByCreatedAtDesc(user)) {
            Complaint c = f.getComplaint();
            items.add(new ActivityItem(f.getCreatedAt(), "Feedback", "bi-star",
                    "Rated '" + c.getTitle() + "' " + f.getRating() + "/5"
                            + (f.getComments() != null ? " - " + shorten(f.getComments()) : ""), c.getId()));
        }

        items.sort((a, b) -> {
            if (a.getTime() == null) {
                return b.getTime() == null ? 0 : 1;
            }
            if (b.getTime() == null) {
                return -1;
            }
            return b.getTime().compareTo(a.getTime()); // newest first
        });
        return items.size() > LIMIT ? new ArrayList<>(items.subList(0, LIMIT)) : items;
    }

    private String shorten(String text) {
        return text.length() <= 120 ? text : text.substring(0, 117) + "...";
    }
}
