package com.civictrack.repository;

import com.civictrack.entity.Category;
import com.civictrack.entity.Complaint;
import com.civictrack.entity.User;
import com.civictrack.enums.ComplaintStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ComplaintRepository extends JpaRepository<Complaint, UUID>, JpaSpecificationExecutor<Complaint> {
    List<Complaint> findByCitizenOrderByCreatedAtDesc(User citizen);

    List<Complaint> findTop10ByOrderByCreatedAtDesc();

    long countByStatus(ComplaintStatus status);

    boolean existsByCitizen(User citizen);

    /** Rows of [categoryName, count], largest first. Used by the dashboard charts. */
    @Query("select c.category.name, count(c) from Complaint c group by c.category.name order by count(c) desc")
    List<Object[]> countByCategory();

    Page<Complaint> findByCitizen(User citizen, Pageable pageable);

    List<Complaint> findByCategoryAndStatusInAndCreatedAtAfter(Category category,
                                                               Collection<ComplaintStatus> statuses,
                                                               LocalDateTime createdAt);
}
