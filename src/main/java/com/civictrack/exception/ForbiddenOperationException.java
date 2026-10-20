package com.civictrack.exception;

/** The logged-in user is not allowed to do this (e.g. viewing another citizen's complaint). */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
