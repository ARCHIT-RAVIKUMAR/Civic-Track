package com.civictrack.controller;

import com.civictrack.entity.Notification;
import com.civictrack.service.InboxService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/** Makes the notification bell data available to the navbar on every page. */
@ControllerAdvice
public class GlobalModelAttributes {

    private final InboxService inboxService;

    public GlobalModelAttributes(InboxService inboxService) {
        this.inboxService = inboxService;
    }

    @ModelAttribute("unreadCount")
    public long unreadCount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return 0;
        }
        try {
            return inboxService.countUnread(authentication.getName());
        } catch (RuntimeException ex) {
            return 0; // the navbar badge must never break a page
        }
    }

    @ModelAttribute("recentNotifications")
    public List<Notification> recentNotifications(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return List.of();
        }
        try {
            return inboxService.list(authentication.getName(), 0, 5).getContent();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }
}
