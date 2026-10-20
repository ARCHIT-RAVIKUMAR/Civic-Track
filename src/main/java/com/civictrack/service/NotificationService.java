package com.civictrack.service;

import com.civictrack.entity.User;

/**
 * ABSTRACTION + POLYMORPHISM: callers only know "notify a user". HOW it is delivered
 * (log line, e-mail, ...) is decided by which implementation Spring plugs in.
 */
public interface NotificationService {
    void notifyUser(User recipient, String subject, String message);
}
