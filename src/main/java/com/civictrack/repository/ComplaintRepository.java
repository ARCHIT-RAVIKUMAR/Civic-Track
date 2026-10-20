package com.civictrack.repository;

import com.civictrack.entity.Complaint;
import com.civictrack.entity.User;
import com.civictrack.enums.ComplaintStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface ComplaintRepository extends JpaRepository<Complaint, UUID>, JpaSpecificationExecutor<Complaint> {
    List<Complaint> findByCitizenOrderByCreatedAtDesc(User citizen);

    List<Complaint> findTop10ByOrderByCreatedAtDesc();

    long countByStatus(ComplaintStatus status);
}
