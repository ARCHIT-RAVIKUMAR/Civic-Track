package com.civictrack.entity;

import com.civictrack.enums.ComplaintStatus;
import com.civictrack.enums.Priority;
import com.civictrack.enums.Role;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComplaintTest {

    private Complaint complaint;

    @BeforeEach
    void setUp() {
        User citizen = new User("Asha", "asha@example.com", "hash", Role.CITIZEN);
        Category category = new Category("Pothole", "Roads");
        complaint = new Complaint("Big pothole", "Deep pothole near the bus stop", "MG Road", category, citizen);
    }

    @Test
    void newComplaintStartsSubmittedWithMediumPriority() {
        assertEquals(ComplaintStatus.SUBMITTED, complaint.getStatus());
        assertEquals(Priority.MEDIUM, complaint.getPriority());
    }

    @Test
    void validTransitionChangesStatus() {
        complaint.changeStatus(ComplaintStatus.UNDER_REVIEW);
        assertEquals(ComplaintStatus.UNDER_REVIEW, complaint.getStatus());
    }

    @Test
    void invalidTransitionIsRejected() {
        assertThrows(InvalidStatusTransitionException.class,
                () -> complaint.changeStatus(ComplaintStatus.CLOSED));
        assertEquals(ComplaintStatus.SUBMITTED, complaint.getStatus());
    }

    @Test
    void rejectNeedsAReason() {
        assertThrows(BusinessRuleException.class, () -> complaint.reject("  "));
        assertEquals(ComplaintStatus.SUBMITTED, complaint.getStatus());
    }

    @Test
    void rejectStoresReasonAndStatus() {
        complaint.reject("Duplicate of an earlier complaint");
        assertEquals(ComplaintStatus.REJECTED, complaint.getStatus());
        assertEquals("Duplicate of an earlier complaint", complaint.getRejectionReason());
    }

    @Test
    void constructorRejectsBlankTitle() {
        User citizen = new User("Asha", "asha@example.com", "hash", Role.CITIZEN);
        assertThrows(IllegalArgumentException.class,
                () -> new Complaint(" ", "desc", "loc", new Category("X", null), citizen));
    }
}
