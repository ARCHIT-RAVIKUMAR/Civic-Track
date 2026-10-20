package com.civictrack.repository;

import com.civictrack.entity.Assignment;
import com.civictrack.entity.Complaint;
import com.civictrack.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    Optional<Assignment> findByComplaintAndActiveTrue(Complaint complaint);

    boolean existsByComplaintAndWorkerAndActiveTrue(Complaint complaint, User worker);

    @Query("select a.complaint from Assignment a where a.worker = :worker and a.active = true order by a.createdAt desc")
    List<Complaint> findComplaintsForWorker(@Param("worker") User worker);
}
