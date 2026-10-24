package com.civictrack.service;

import com.civictrack.entity.ActivityLog;
import com.civictrack.entity.User;
import com.civictrack.enums.ActivityType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Records account events and builds the per-user activity timeline for administrators. */
public interface ActivityService {

    void record(User user, ActivityType type, String details, User actor);

    /** Used by the login listener. Unknown e-mail addresses are ignored. */
    void recordLoginEvent(String email, ActivityType type);

    /** Removes a deleted user's history (called by UserService.deleteUser). */
    void forget(User user);

    Optional<ActivityLog> lastLogin(User user);

    /** Newest first: account events + complaints, status changes, comments and feedback by that user. */
    List<ActivityItem> timeline(UUID userId, User admin);
}
