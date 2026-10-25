package com.civictrack.repository;

import com.civictrack.entity.Complaint;
import com.civictrack.entity.StatusHistory;
import com.civictrack.entity.User;
import com.civictrack.enums.ComplaintStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, UUID> {
    List<StatusHistory> findByComplaintOrderByCreatedAtAsc(Complaint complaint);

    boolean existsByChangedBy(User changedBy);

    List<StatusHistory> findTop500ByChangedByAndNewStatusOrderByCreatedAtDesc(User changedBy, ComplaintStatus newStatus);

    List<StatusHistory> findTop200ByChangedByOrderByCreatedAtDesc(User changedBy);
}
