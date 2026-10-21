package com.civictrack.controller;

import com.civictrack.service.InboxService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Makes the unread-notification count available to the navbar on every page. */
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
}
