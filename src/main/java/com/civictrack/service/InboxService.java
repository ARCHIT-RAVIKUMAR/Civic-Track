package com.civictrack.service;

import com.civictrack.entity.Notification;
import org.springframework.data.domain.Page;

import java.util.UUID;

/** Reading and managing the notifications of the logged-in user. */
public interface InboxService {
    long countUnread(String email);

    Page<Notification> list(String email, int page, int size);

    /** Marks one notification as read (only its owner may do this) and returns it. */
    Notification markRead(UUID notificationId, String email);

    void markAllRead(String email);
}
