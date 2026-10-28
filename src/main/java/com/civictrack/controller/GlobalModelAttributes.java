package com.civictrack.controller;

import com.civictrack.entity.Announcement;
import com.civictrack.entity.Notification;
import com.civictrack.service.AnnouncementService;
import com.civictrack.service.InboxService;
import com.civictrack.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/** Makes the notification bell data available to the navbar on every page. */
@ControllerAdvice
public class GlobalModelAttributes {

    private final InboxService inboxService;
    private final AnnouncementService announcementService;
    private final UserService userService;

    public GlobalModelAttributes(InboxService inboxService, AnnouncementService announcementService,
                                 UserService userService) {
        this.inboxService = inboxService;
        this.announcementService = announcementService;
        this.userService = userService;
    }

    /** The logged-in user (for the navbar name and the first-login welcome guide). Null for visitors. */
    @ModelAttribute("currentUser")
    public com.civictrack.entity.User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        try {
            return userService.getByEmail(authentication.getName());
        } catch (RuntimeException ex) {
            return null; // the navbar falls back to the e-mail address
        }
    }

    @ModelAttribute("announcements")
    public List<Announcement> announcements() {
        try {
            return announcementService.findCurrent();
        } catch (RuntimeException ex) {
            return List.of(); // a notice must never break a page
        }
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
