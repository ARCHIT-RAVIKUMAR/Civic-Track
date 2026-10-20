package com.civictrack.exception;

import com.civictrack.enums.ComplaintStatus;

public class InvalidStatusTransitionException extends BusinessRuleException {
    public InvalidStatusTransitionException(ComplaintStatus from, ComplaintStatus to) {
        super("A complaint cannot move from " + from.getLabel() + " to " + to.getLabel() + ".");
    }
}
