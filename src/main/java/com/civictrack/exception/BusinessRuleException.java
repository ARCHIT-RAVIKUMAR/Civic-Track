package com.civictrack.exception;

/** A rule of the application was broken (e.g. "rejection needs a reason"). Message is safe to show to users. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
