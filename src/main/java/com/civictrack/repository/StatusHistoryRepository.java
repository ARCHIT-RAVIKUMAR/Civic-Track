package com.civictrack.repository;

import com.civictrack.entity.Complaint;
import com.civictrack.entity.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, UUID> {
    List<StatusHistory> findByComplaintOrderByCreatedAtAsc(Complaint complaint);
}
