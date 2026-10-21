package com.civictrack.service;

import com.civictrack.entity.Notification;
import com.civictrack.entity.User;
import com.civictrack.enums.Role;
import com.civictrack.exception.ForbiddenOperationException;
import com.civictrack.repository.NotificationRepository;
import com.civictrack.repository.UserRepository;
import com.civictrack.service.impl.InboxServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboxServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;

    private InboxServiceImpl service;
    private User owner;
    private Notification notification;
    private final UUID notificationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new InboxServiceImpl(notificationRepository, userRepository);
        owner = new User("Owner", "owner@example.com", "hash", Role.AUTHORITY);
        ReflectionTestUtils.setField(owner, "id", UUID.randomUUID());
        notification = new Notification(owner, null, "Title", "Message");
    }

    @Test
    void ownerCanMarkNotificationAsRead() {
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        assertFalse(notification.isRead());

        service.markRead(notificationId, "Owner@Example.com");

        assertTrue(notification.isRead());
    }

    @Test
    void otherUsersCannotTouchSomeoneElsesNotification() {
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        assertThrows(ForbiddenOperationException.class, () -> service.markRead(notificationId, "intruder@example.com"));
        assertFalse(notification.isRead());
    }

    @Test
    void unreadCountIsZeroForUnknownUser() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertEquals(0L, service.countUnread("ghost@example.com"));
    }
}
