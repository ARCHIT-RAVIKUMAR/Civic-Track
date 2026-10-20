package com.civictrack.repository;

import com.civictrack.dto.ComplaintFilter;
import com.civictrack.entity.Complaint;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Builds the WHERE clause only from the filters the user actually filled in. */
public final class ComplaintSpecs {

    private ComplaintSpecs() {
    }

    public static Specification<Complaint> withFilter(ComplaintFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
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
