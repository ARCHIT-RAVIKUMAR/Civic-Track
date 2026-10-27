package com.civictrack.service.impl;

import com.civictrack.dto.FeedbackForm;
import com.civictrack.entity.Assignment;
import com.civictrack.entity.Complaint;
import com.civictrack.entity.Feedback;
import com.civictrack.entity.User;
import com.civictrack.enums.ComplaintStatus;
import com.civictrack.enums.Role;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.exception.ForbiddenOperationException;
import com.civictrack.exception.ResourceNotFoundException;
import com.civictrack.repository.AssignmentRepository;
import com.civictrack.repository.ComplaintRepository;
import com.civictrack.repository.FeedbackRepository;
import com.civictrack.service.FeedbackService;
import com.civictrack.service.NotificationService;
import com.civictrack.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class FeedbackServiceImpl implements FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackServiceImpl.class);

    private final FeedbackRepository feedbackRepository;
    private final ComplaintRepository complaintRepository;
    private final AssignmentRepository assignmentRepository;
    private final UserService userService;
    private final List<NotificationService> notifiers;

    public FeedbackServiceImpl(FeedbackRepository feedbackRepository, ComplaintRepository complaintRepository,
                               AssignmentRepository assignmentRepository, UserService userService,
                               List<NotificationService> notifiers) {
        this.feedbackRepository = feedbackRepository;
        this.complaintRepository = complaintRepository;
        this.assignmentRepository = assignmentRepository;
        this.userService = userService;
        this.notifiers = notifiers;
    }

    @Override
    public Feedback submit(UUID complaintId, FeedbackForm form, User citizen) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint not found."));
        if (!citizen.hasRole(Role.CITIZEN) || !complaint.isOwnedBy(citizen)) {
            throw new ForbiddenOperationException("You can only give feedback on your own complaints.");
        }
        ComplaintStatus status = complaint.getStatus();
        if (status != ComplaintStatus.RESOLVED && status != ComplaintStatus.CLOSED) {
            throw new BusinessRuleException("Feedback can be given once the complaint is resolved.");
        }
        if (feedbackRepository.existsByComplaint(complaint)) {
            throw new BusinessRuleException("You have already given feedback for this complaint.");
        }
        Feedback saved = feedbackRepository.save(new Feedback(complaint, citizen, form.getRating(), form.getComments()));

        // the worker who did the job and the officers hear how it went
        String subject = "Feedback received: " + complaint.getTitle();
        String body = citizen.getFullName() + " rated the work " + form.getRating() + "/5"
                + (form.getComments() == null || form.getComments().isBlank() ? "." : ": " + form.getComments().trim());
        assignmentRepository.findByComplaintAndActiveTrue(complaint).map(Assignment::getWorker)
                .ifPresent(worker -> notify(worker, subject, body, complaint));
        for (User officer : userService.findActiveByRole(Role.AUTHORITY)) {
            notify(officer, subject, body, complaint);
        }
        return saved;
    }

    private void notify(User recipient, String subject, String message, Complaint complaint) {
        for (NotificationService notifier : notifiers) {
            try {
                notifier.notifyUser(recipient, subject, message, complaint);
            } catch (RuntimeException ex) {
                log.warn("Notification via {} failed: {}", notifier.getClass().getSimpleName(), ex.getMessage());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Feedback> findByComplaint(Complaint complaint) {
        return feedbackRepository.findByComplaint(complaint);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canSubmitFeedback(Complaint complaint, User viewer) {
        return viewer.hasRole(Role.CITIZEN)
                && complaint.isOwnedBy(viewer)
                && (complaint.getStatus() == ComplaintStatus.RESOLVED || complaint.getStatus() == ComplaintStatus.CLOSED)
                && !feedbackRepository.existsByComplaint(complaint);
    }
}
