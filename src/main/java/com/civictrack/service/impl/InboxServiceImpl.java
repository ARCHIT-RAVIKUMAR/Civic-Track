package com.civictrack.service.impl;

import com.civictrack.entity.Notification;
import com.civictrack.entity.User;
import com.civictrack.exception.ForbiddenOperationException;
import com.civictrack.exception.ResourceNotFoundException;
import com.civictrack.repository.NotificationRepository;
import com.civictrack.repository.UserRepository;
import com.civictrack.service.InboxService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class InboxServiceImpl implements InboxService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public InboxServiceImpl(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(String email) {
        return userRepository.findByEmail(normalize(email))
                .map(notificationRepository::countByRecipientAndReadFalse)
                .orElse(0L);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Notification> list(String email, int page, int size) {
        User user = findUser(email);
        return notificationRepository.findByRecipientOrderByCreatedAtDesc(user, PageRequest.of(Math.max(page, 0), size));
    }

    @Override
    public Notification markRead(UUID notificationId, String email) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found."));
        if (!notification.getRecipient().getEmail().equals(normalize(email))) {
            throw new ForbiddenOperationException("This notification belongs to someone else.");
        }
        notification.markRead();
        return notificationRepository.save(notification);
    }

    @Override
    public void markAllRead(String email) {
        User user = findUser(email);
        List<Notification> unread = notificationRepository.findByRecipientAndReadFalse(user);
        unread.forEach(Notification::markRead);
        notificationRepository.saveAll(unread);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(normalize(email))
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
