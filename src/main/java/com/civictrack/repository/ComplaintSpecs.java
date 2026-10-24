package com.civictrack.repository;

import com.civictrack.dto.ComplaintFilter;
import com.civictrack.entity.Complaint;
import com.civictrack.enums.ComplaintStatus;
import com.civictrack.enums.Priority;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the WHERE clause only from the filters the user actually filled in. */
public final class ComplaintSpecs {

    private static final List<ComplaintStatus> OPEN_STATUSES = List.of(
            ComplaintStatus.SUBMITTED, ComplaintStatus.UNDER_REVIEW,
            ComplaintStatus.ASSIGNED, ComplaintStatus.IN_PROGRESS);

    private ComplaintSpecs() {
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public static Specification<Complaint> withFilter(ComplaintFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.getQ() != null && !filter.getQ().isBlank()) {
                String pattern = "%" + escapeLike(filter.getQ().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("location")), pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("description")), pattern, '\\')));
            }
            if (filter.isOverdue()) {
                LocalDateTime now = LocalDateTime.now();
                List<Predicate> perPriority = new ArrayList<>();
                for (Priority p : Priority.values()) {
                    perPriority.add(cb.and(
                            cb.equal(root.get("priority"), p),
                            cb.lessThan(root.<LocalDateTime>get("createdAt"), now.minusDays(p.getSlaDays()))));
                }
                predicates.add(cb.or(perPriority.toArray(new Predicate[0])));
                predicates.add(root.get("status").in(OPEN_STATUSES));
            }
            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }
            if (filter.getCategoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), filter.getCategoryId()));
            }
            if (filter.getFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("createdAt"),
                        filter.getFrom().atStartOfDay()));
            }
            if (filter.getTo() != null) {
                predicates.add(cb.lessThan(root.<LocalDateTime>get("createdAt"),
                        filter.getTo().plusDays(1).atStartOfDay()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
