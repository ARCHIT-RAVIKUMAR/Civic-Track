package com.civictrack.service.impl;

import com.civictrack.entity.Complaint;
import com.civictrack.entity.User;
import com.civictrack.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Writes notifications to the application log. Needs no extra setup. */
@Service
@ConditionalOnProperty(name = "civictrack.notification.mode", havingValue = "log", matchIfMissing = true)
public class LogNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationService.class);

    @Override
    public void notifyUser(User recipient, String subject, String message, Complaint complaint) {
        log.info("NOTIFICATION to {}: {} - {}", recipient.getEmail(), subject, message);
    }
}
