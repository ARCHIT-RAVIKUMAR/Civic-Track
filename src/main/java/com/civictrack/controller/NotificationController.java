package com.civictrack.controller;

import com.civictrack.entity.Notification;
import com.civictrack.service.InboxService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private static final int PAGE_SIZE = 15;

    private final InboxService inboxService;

    public NotificationController(InboxService inboxService) {
        this.inboxService = inboxService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Authentication auth, Model model) {
        Page<Notification> results = inboxService.list(auth.getName(), page, PAGE_SIZE);
        model.addAttribute("page", results);
        return "notifications/list";
    }

    /** Polled by the page every 30 seconds so the bell can show new activity without a manual refresh. */
    @GetMapping("/unread-count")
    @ResponseBody
    public Map<String, Long> unreadCount(Authentication auth) {
        return Map.of("count", inboxService.countUnread(auth.getName()));
    }

    /** Marks the notification as read, then opens the related complaint (if any). */
    @PostMapping("/{id}/open")
    public String open(@PathVariable UUID id, Authentication auth) {
        Notification notification = inboxService.markRead(id, auth.getName());
        if (notification.getComplaint() != null) {
            return "redirect:/complaints/" + notification.getComplaint().getId();
        }
        return "redirect:/notifications";
    }

    /** Marks one notification as read WITHOUT opening it. The page calls this in the background (fetch). */
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id, Authentication auth,
                                         @RequestHeader(value = "X-Requested-With", required = false) String requestedWith) {
        inboxService.markRead(id, auth.getName());
        if ("fetch".equals(requestedWith)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, "/notifications").build();
    }

    @PostMapping("/read-all")
    public String readAll(Authentication auth) {
        inboxService.markAllRead(auth.getName());
        return "redirect:/notifications";
    }
}
