package com.civictrack.entity;

import com.civictrack.enums.ComplaintStatus;
import com.civictrack.enums.Priority;
import com.civictrack.enums.Role;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

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

    @Test
    void validCoordinatesAreStored() {
        complaint.locate(9.9312, 76.2673);
        assertTrue(complaint.hasCoordinates());
    }

    @Test
    void coordinatesMustComeAsAPairAndBeInRange() {
        assertThrows(IllegalArgumentException.class, () -> complaint.locate(9.9, null));
        assertThrows(IllegalArgumentException.class, () -> complaint.locate(120.0, 10.0));
        assertThrows(IllegalArgumentException.class, () -> complaint.locate(10.0, 200.0));
    }

    @Test
    void nullCoordinatesClearThePosition() {
        complaint.locate(9.9, 76.2);
        complaint.locate(null, null);
        assertFalse(complaint.hasCoordinates());
    }

    private void createdDaysAgo(int days) {
        ReflectionTestUtils.setField(complaint, "createdAt", LocalDateTime.now().minusDays(days));
    }

    @Test
    void complaintBecomesOverdueAfterItsPriorityTarget() {
        createdDaysAgo(8);                       // MEDIUM target is 7 days
        assertTrue(complaint.isOverdue());
        complaint.changePriority(Priority.LOW);  // LOW target is 14 days
        assertFalse(complaint.isOverdue());
        complaint.changePriority(Priority.URGENT);
        assertTrue(complaint.isOverdue());
    }

    @Test
    void newComplaintIsNotOverdue() {
        createdDaysAgo(0);
        assertFalse(complaint.isOverdue());
    }

    @Test
    void finishedOrResolvedComplaintsAreNeverOverdue() {
        createdDaysAgo(30);
        complaint.reject("Duplicate");
        assertFalse(complaint.isOverdue());
    }

    @Test
    void everyNewComplaintGetsAReferenceNumber() {
        assertTrue(complaint.getReferenceNumber().matches("CT-[A-HJKMNP-Z2-9]{4}-[A-HJKMNP-Z2-9]{4}"),
                complaint.getReferenceNumber());
    }
}
