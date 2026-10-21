package com.civictrack.service.impl;

import com.civictrack.entity.Complaint;
import com.civictrack.entity.Notification;
import com.civictrack.entity.User;
import com.civictrack.repository.NotificationRepository;
import com.civictrack.service.NotificationService;
import org.springframework.stereotype.Service;

/** Always on: stores the notification so it appears under the bell icon in the navbar. */
@Service
public class InAppNotificationService implements NotificationService {

    private final NotificationRepository notificationRepository;

    public InAppNotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public void notifyUser(User recipient, String subject, String message, Complaint complaint) {
        notificationRepository.save(new Notification(recipient, complaint, subject, message));
    }
}
