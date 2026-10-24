package com.civictrack.service;

import com.civictrack.entity.ActivityLog;
import com.civictrack.entity.Category;
import com.civictrack.entity.Complaint;
import com.civictrack.entity.ComplaintComment;
import com.civictrack.entity.User;
import com.civictrack.enums.ActivityType;
import com.civictrack.enums.Role;
import com.civictrack.exception.ForbiddenOperationException;
import com.civictrack.repository.ActivityLogRepository;
import com.civictrack.repository.ComplaintCommentRepository;
import com.civictrack.repository.ComplaintRepository;
import com.civictrack.repository.FeedbackRepository;
import com.civictrack.repository.StatusHistoryRepository;
import com.civictrack.repository.UserRepository;
import com.civictrack.service.impl.ActivityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityServiceImplTest {

    @Mock private ActivityLogRepository activityLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private ComplaintRepository complaintRepository;
    @Mock private StatusHistoryRepository historyRepository;
    @Mock private ComplaintCommentRepository commentRepository;
    @Mock private FeedbackRepository feedbackRepository;

    private ActivityServiceImpl service;
    private User admin;
    private User citizen;

    private static User userWithId(String name, String email, Role role) {
        User u = new User(name, email, "hash", role);
        ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
        return u;
    }

    private static <T> T at(T entity, LocalDateTime time) {
        ReflectionTestUtils.setField(entity, "createdAt", time);
        return entity;
    }

    @BeforeEach
    void setUp() {
        service = new ActivityServiceImpl(activityLogRepository, userRepository, complaintRepository,
                historyRepository, commentRepository, feedbackRepository);
        admin = userWithId("Admin", "admin@example.com", Role.ADMIN);
        citizen = userWithId("Citizen", "citizen@example.com", Role.CITIZEN);
    }

    @Test
    void timelineMergesAllSourcesNewestFirst() {
        Complaint complaint = at(new Complaint("Broken light", "Light is out for days", "5th Cross",
                new Category("Streetlight", null), citizen), LocalDateTime.of(2026, 1, 1, 9, 0));
        ActivityLog login = at(new ActivityLog(citizen, ActivityType.LOGIN_SUCCESS, null, null),
                LocalDateTime.of(2026, 1, 1, 10, 0));
        ComplaintComment comment = at(new ComplaintComment(complaint, citizen, "Still not fixed"),
                LocalDateTime.of(2026, 1, 1, 11, 0));

        when(userRepository.findById(citizen.getId())).thenReturn(Optional.of(citizen));
        when(activityLogRepository.findTop200ByUserOrderByCreatedAtDesc(citizen)).thenReturn(List.of(login));
        when(complaintRepository.findByCitizenOrderByCreatedAtDesc(citizen)).thenReturn(List.of(complaint));
        when(commentRepository.findTop200ByAuthorOrderByCreatedAtDesc(citizen)).thenReturn(List.of(comment));

        List<ActivityItem> items = service.timeline(citizen.getId(), admin);

        assertEquals(3, items.size());
        assertEquals("Comment", items.get(0).getKind());
        assertEquals("Security", items.get(1).getKind());
        assertEquals("Complaint", items.get(2).getKind());
        assertTrue(items.get(2).getDescription().contains("Broken light"));
    }

    @Test
    void adminActionsShowWhoDidThem() {
        ActivityLog roleChange = at(new ActivityLog(citizen, ActivityType.ROLE_CHANGED, "From Citizen to Field Worker", admin),
                LocalDateTime.of(2026, 1, 1, 10, 0));
        when(userRepository.findById(citizen.getId())).thenReturn(Optional.of(citizen));
        when(activityLogRepository.findTop200ByUserOrderByCreatedAtDesc(citizen)).thenReturn(List.of(roleChange));

        List<ActivityItem> items = service.timeline(citizen.getId(), admin);

        assertTrue(items.get(0).getDescription().contains("(by Admin)"));
    }

    @Test
    void onlyAdministratorsCanViewTimelines() {
        assertThrows(ForbiddenOperationException.class, () -> service.timeline(citizen.getId(), citizen));
    }

    @Test
    void loginEventsForUnknownEmailsAreIgnored() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        service.recordLoginEvent("ghost@example.com", ActivityType.LOGIN_FAILED);

        verify(activityLogRepository, never()).save(any(ActivityLog.class));
    }

    @Test
    void loginEventsForKnownUsersAreSaved() {
        when(userRepository.findByEmail("citizen@example.com")).thenReturn(Optional.of(citizen));

        service.recordLoginEvent("Citizen@Example.com ", ActivityType.LOGIN_SUCCESS);

        verify(activityLogRepository).save(any(ActivityLog.class));
    }
}
