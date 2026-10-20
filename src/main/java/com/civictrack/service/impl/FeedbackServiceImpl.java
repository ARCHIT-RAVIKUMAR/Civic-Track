package com.civictrack.service.impl;

import com.civictrack.dto.FeedbackForm;
import com.civictrack.entity.Complaint;
import com.civictrack.entity.Feedback;
import com.civictrack.entity.User;
import com.civictrack.enums.ComplaintStatus;
import com.civictrack.enums.Role;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.exception.ForbiddenOperationException;
import com.civictrack.exception.ResourceNotFoundException;
import com.civictrack.repository.ComplaintRepository;
import com.civictrack.repository.FeedbackRepository;
import com.civictrack.service.FeedbackService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final ComplaintRepository complaintRepository;

    public FeedbackServiceImpl(FeedbackRepository feedbackRepository, ComplaintRepository complaintRepository) {
        this.feedbackRepository = feedbackRepository;
        this.complaintRepository = complaintRepository;
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
        return feedbackRepository.save(new Feedback(complaint, citizen, form.getRating(), form.getComments()));
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
