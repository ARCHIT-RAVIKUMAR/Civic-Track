package com.civictrack.service.impl;

import com.civictrack.dto.AnnouncementForm;
import com.civictrack.entity.Announcement;
import com.civictrack.entity.User;
import com.civictrack.enums.Role;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.exception.ForbiddenOperationException;
import com.civictrack.exception.ResourceNotFoundException;
import com.civictrack.repository.AnnouncementRepository;
import com.civictrack.service.AnnouncementService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AnnouncementServiceImpl implements AnnouncementService {

    private final AnnouncementRepository announcementRepository;

    public AnnouncementServiceImpl(AnnouncementRepository announcementRepository) {
        this.announcementRepository = announcementRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Announcement> findCurrent() {
        return announcementRepository.findCurrent(LocalDateTime.now(), PageRequest.of(0, 3));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Announcement> findAll(User admin) {
        requireAdmin(admin);
        return announcementRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public Announcement create(AnnouncementForm form, User admin) {
        requireAdmin(admin);
        LocalDateTime expiresAt = null;
        LocalDate expiresOn = form.getExpiresOn();
        if (expiresOn != null) {
            if (expiresOn.isBefore(LocalDate.now())) {
                throw new BusinessRuleException("The end date cannot be in the past.");
            }
            expiresAt = expiresOn.atTime(LocalTime.MAX); // visible for the whole of that day
        }
        return announcementRepository.save(
                new Announcement(form.getTitle(), form.getMessage(), form.getSeverity(), expiresAt, admin));
    }

    @Override
    public void toggle(UUID id, User admin) {
        requireAdmin(admin);
        Announcement announcement = find(id);
        announcement.toggleActive();
        announcementRepository.save(announcement);
    }

    @Override
    public void delete(UUID id, User admin) {
        requireAdmin(admin);
        announcementRepository.delete(find(id));
    }

    private Announcement find(UUID id) {
        return announcementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found."));
    }

    private void requireAdmin(User actor) {
        if (actor == null || !actor.hasRole(Role.ADMIN)) {
            throw new ForbiddenOperationException("Only administrators can manage announcements.");
        }
    }
}
