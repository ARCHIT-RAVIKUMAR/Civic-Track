package com.civictrack.service;

import com.civictrack.dto.FeedbackForm;
import com.civictrack.entity.Complaint;
import com.civictrack.entity.Feedback;
import com.civictrack.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface FeedbackService {
    Feedback submit(UUID complaintId, FeedbackForm form, User citizen);

    Optional<Feedback> findByComplaint(Complaint complaint);

    boolean canSubmitFeedback(Complaint complaint, User viewer);
}
