package com.civictrack.repository;

import com.civictrack.entity.Complaint;
import com.civictrack.entity.ComplaintComment;
import com.civictrack.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ComplaintCommentRepository extends JpaRepository<ComplaintComment, UUID> {
    List<ComplaintComment> findByComplaintOrderByCreatedAtAsc(Complaint complaint);

    boolean existsByAuthor(User author);

    List<ComplaintComment> findTop200ByAuthorOrderByCreatedAtDesc(User author);
}
