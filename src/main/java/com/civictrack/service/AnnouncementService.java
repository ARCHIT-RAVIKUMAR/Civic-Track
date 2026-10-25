package com.civictrack.service;

import com.civictrack.dto.AnnouncementForm;
import com.civictrack.entity.Announcement;
import com.civictrack.entity.User;

import java.util.List;
import java.util.UUID;

public interface AnnouncementService {
    /** Active, not expired - newest first, at most 3. Visible to everyone. */
    List<Announcement> findCurrent();

    List<Announcement> findAll(User admin);

    Announcement create(AnnouncementForm form, User admin);

    void toggle(UUID id, User admin);

    void delete(UUID id, User admin);
}
