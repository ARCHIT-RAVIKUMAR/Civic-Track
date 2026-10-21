package com.civictrack.service;

import com.civictrack.entity.Complaint;
import com.civictrack.entity.User;

/**
 * ABSTRACTION + POLYMORPHISM: callers only know "notify a user". HOW it is delivered
 * (in-app bell, log line, e-mail ...) is decided by the implementations. ComplaintServiceImpl
 * receives ALL implementations and simply calls each one.
 */
public interface NotificationService {
    /** @param complaint the related complaint, or null */
    void notifyUser(User recipient, String subject, String message, Complaint complaint);
}
