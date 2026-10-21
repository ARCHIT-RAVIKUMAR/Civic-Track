package com.civictrack.repository;

import com.civictrack.entity.Notification;
import com.civictrack.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientOrderByCreatedAtDesc(User recipient, Pageable pageable);

    long countByRecipientAndReadFalse(User recipient);

    List<Notification> findByRecipientAndReadFalse(User recipient);

    void deleteByRecipient(User recipient);
}
