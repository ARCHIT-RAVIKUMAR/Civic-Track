package com.civictrack.service;

import com.civictrack.dto.FeedbackForm;
import com.civictrack.entity.Assignment;
import com.civictrack.entity.Category;
import com.civictrack.entity.Complaint;
import com.civictrack.entity.User;
import com.civictrack.enums.ComplaintStatus;
import com.civictrack.enums.Role;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.exception.ForbiddenOperationException;
import com.civictrack.repository.AssignmentRepository;
import com.civictrack.repository.ComplaintRepository;
import com.civictrack.repository.FeedbackRepository;
import com.civictrack.service.impl.FeedbackServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceImplTest {

    @Mock private FeedbackRepository feedbackRepository;
    @Mock private ComplaintRepository complaintRepository;
    @Mock private AssignmentRepository assignmentRepository;
    @Mock private UserService userService;
    @Mock private NotificationService notificationService;

    private FeedbackServiceImpl service;
    private User citizen;
    private User worker;
    private User authority;
    private Complaint complaint;
    private final UUID complaintId = UUID.randomUUID();

    private static User userWithId(String name, String email, Role role) {
        User u = new User(name, email, "hash", role);
        ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
        return u;
    }

    private FeedbackForm form(int rating, String comments) {
        FeedbackForm f = new FeedbackForm();
        f.setRating(rating);
        f.setComments(comments);
        return f;
    }

    @BeforeEach
    void setUp() {
        service = new FeedbackServiceImpl(feedbackRepository, complaintRepository, assignmentRepository,
                userService, List.of(notificationService));
        citizen = userWithId("Citizen", "c@example.com", Role.CITIZEN);
        worker = userWithId("Worker", "w@example.com", Role.FIELD_WORKER);
        authority = userWithId("Authority", "a@example.com", Role.AUTHORITY);
        complaint = new Complaint("Light out", "No light for days", "5th Cross", new Category("Light", null), citizen);
    }

    private void resolve() {
        complaint.changeStatus(ComplaintStatus.UNDER_REVIEW);
        complaint.changeStatus(ComplaintStatus.ASSIGNED);
        complaint.changeStatus(ComplaintStatus.IN_PROGRESS);
        complaint.changeStatus(ComplaintStatus.RESOLVED);
    }

    @Test
    void feedbackIsSentToTheWorkerAndTheOfficers() {
        resolve();
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(feedbackRepository.existsByComplaint(complaint)).thenReturn(false);
        when(feedbackRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.findByComplaintAndActiveTrue(complaint))
                .thenReturn(Optional.of(new Assignment(complaint, worker, authority, null)));
        when(userService.findActiveByRole(Role.AUTHORITY)).thenReturn(List.of(authority));

        service.submit(complaintId, form(5, "Great job"), citizen);

        verify(notificationService).notifyUser(eq(worker), anyString(), anyString(), any());
        verify(notificationService).notifyUser(eq(authority), anyString(), anyString(), any());
    }

    @Test
    void feedbackIsRefusedBeforeTheComplaintIsResolved() {
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));

        assertThrows(BusinessRuleException.class, () -> service.submit(complaintId, form(4, null), citizen));
    }

    @Test
    void onlyTheOwnerCanGiveFeedback() {
        User other = userWithId("Other", "o@example.com", Role.CITIZEN);
        resolve();
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));

        assertThrows(ForbiddenOperationException.class, () -> service.submit(complaintId, form(4, null), other));
    }
}
