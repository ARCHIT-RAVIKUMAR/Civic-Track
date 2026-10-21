package com.civictrack.repository;

import com.civictrack.entity.Complaint;
import com.civictrack.entity.Feedback;
import com.civictrack.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    Optional<Feedback> findByComplaint(Complaint complaint);

    boolean existsByComplaint(Complaint complaint);

    boolean existsByCitizen(User citizen);
}
