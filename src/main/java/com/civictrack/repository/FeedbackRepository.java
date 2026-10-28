package com.civictrack.repository;

import com.civictrack.entity.Complaint;
import com.civictrack.entity.Feedback;
import com.civictrack.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    Optional<Feedback> findByComplaint(Complaint complaint);

    boolean existsByComplaint(Complaint complaint);

    boolean existsByCitizen(User citizen);

    /** Average rating of all feedback, or null when there is none. */
    @Query("select avg(f.rating) from Feedback f")
    Double averageRating();

    /** Rows of [stars, how many]. */
    @Query("select f.rating, count(f) from Feedback f group by f.rating")
    List<Object[]> countByRating();

    /** Rows of [category name, average rating, how many], best first. */
    @Query("select f.complaint.category.name, avg(f.rating), count(f) from Feedback f "
            + "group by f.complaint.category.name order by avg(f.rating) desc")
    List<Object[]> averageByCategory();

    List<Feedback> findTop200ByCitizenOrderByCreatedAtDesc(User citizen);
}
