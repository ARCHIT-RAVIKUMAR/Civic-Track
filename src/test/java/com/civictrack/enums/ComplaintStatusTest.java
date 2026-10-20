package com.civictrack.enums;

import org.junit.jupiter.api.Test;

import static com.civictrack.enums.ComplaintStatus.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplaintStatusTest {

    @Test
    void happyPathIsAllowedStepByStep() {
        assertTrue(SUBMITTED.canTransitionTo(UNDER_REVIEW));
        assertTrue(UNDER_REVIEW.canTransitionTo(ASSIGNED));
        assertTrue(ASSIGNED.canTransitionTo(IN_PROGRESS));
        assertTrue(IN_PROGRESS.canTransitionTo(RESOLVED));
        assertTrue(RESOLVED.canTransitionTo(CLOSED));
    }

    @Test
    void stepsCannotBeSkipped() {
        assertFalse(SUBMITTED.canTransitionTo(ASSIGNED));
        assertFalse(SUBMITTED.canTransitionTo(CLOSED));
        assertFalse(ASSIGNED.canTransitionTo(RESOLVED));
        assertFalse(IN_PROGRESS.canTransitionTo(CLOSED));
    }

    @Test
    void rejectionOnlyBeforeAssignment() {
        assertTrue(SUBMITTED.canTransitionTo(REJECTED));
        assertTrue(UNDER_REVIEW.canTransitionTo(REJECTED));
        assertFalse(IN_PROGRESS.canTransitionTo(REJECTED));
    }

    @Test
    void authorityCanSendResolvedWorkBack() {
        assertTrue(RESOLVED.canTransitionTo(IN_PROGRESS));
    }

    @Test
    void closedAndRejectedAreTerminal() {
        assertTrue(CLOSED.isTerminal());
        assertTrue(REJECTED.isTerminal());
        assertTrue(CLOSED.allowedNext().isEmpty());
        assertTrue(REJECTED.allowedNext().isEmpty());
        assertFalse(SUBMITTED.isTerminal());
    }
}
