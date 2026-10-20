package com.civictrack.service;

import com.civictrack.entity.Assignment;
import com.civictrack.entity.Category;
import com.civictrack.entity.Complaint;
import com.civictrack.entity.StatusHistory;
import com.civictrack.entity.User;
import com.civictrack.enums.ComplaintStatus;
import com.civictrack.enums.Role;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.exception.ForbiddenOperationException;
import com.civictrack.exception.InvalidStatusTransitionException;
import com.civictrack.repository.AssignmentRepository;
import com.civictrack.repository.ComplaintRepository;
import com.civictrack.repository.StatusHistoryRepository;
import com.civictrack.service.impl.ComplaintServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintServiceImplTest {

    @Mock private ComplaintRepository complaintRepository;
    @Mock private AssignmentRepository assignmentRepository;
    @Mock private StatusHistoryRepository historyRepository;
    @Mock private CategoryService categoryService;
    @Mock private UserService userService;
    @Mock private FileStorageService fileStorageService;
    @Mock private NotificationService notificationService;

    private ComplaintServiceImpl service;
    private User citizen;
    private User authority;
    private User worker;
    private Complaint complaint;
    private final UUID complaintId = UUID.randomUUID();

    private static User userWithId(String name, String email, Role role) {
        User u = new User(name, email, "hash", role);
        ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
        return u;
    }

    @BeforeEach
    void setUp() {
        service = new ComplaintServiceImpl(complaintRepository, assignmentRepository, historyRepository,
                categoryService, userService, fileStorageService, notificationService);
        citizen = userWithId("Citizen", "citizen@example.com", Role.CITIZEN);
        authority = userWithId("Authority", "auth@example.com", Role.AUTHORITY);
        worker = userWithId("Worker", "worker@example.com", Role.FIELD_WORKER);
        complaint = new Complaint("Broken light", "Streetlight is out for a week", "5th Cross",
                new Category("Streetlight", null), citizen);
    }

    @Test
    void assignMovesComplaintToAssignedAndNotifiesEveryone() {
        complaint.changeStatus(ComplaintStatus.UNDER_REVIEW);
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(userService.getById(worker.getId())).thenReturn(worker);
        when(assignmentRepository.findByComplaintAndActiveTrue(complaint)).thenReturn(Optional.empty());

        service.assign(complaintId, worker.getId(), "Please fix today", authority);

        assertEquals(ComplaintStatus.ASSIGNED, complaint.getStatus());
        verify(assignmentRepository).save(any(Assignment.class));
        verify(historyRepository).save(any(StatusHistory.class));
        verify(notificationService).notifyUser(eq(worker), anyString(), anyString());
        verify(notificationService).notifyUser(eq(citizen), anyString(), anyString());
    }

    @Test
    void assignIsRefusedBeforeReview() {
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(userService.getById(worker.getId())).thenReturn(worker);

        assertThrows(BusinessRuleException.class,
                () -> service.assign(complaintId, worker.getId(), null, authority));
        assertEquals(ComplaintStatus.SUBMITTED, complaint.getStatus());
    }

    @Test
    void citizenCannotAssignComplaints() {
        assertThrows(ForbiddenOperationException.class,
                () -> service.assign(complaintId, worker.getId(), null, citizen));
    }

    @Test
    void closeIsRefusedUnlessResolved() {
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));

        assertThrows(InvalidStatusTransitionException.class,
                () -> service.close(complaintId, null, authority));
    }

    @Test
    void rejectWithoutReasonFails() {
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));

        assertThrows(BusinessRuleException.class, () -> service.reject(complaintId, " ", authority));
        assertEquals(ComplaintStatus.SUBMITTED, complaint.getStatus());
    }

    @Test
    void workerCanResolveOwnInProgressComplaint() {
        complaint.changeStatus(ComplaintStatus.UNDER_REVIEW);
        complaint.changeStatus(ComplaintStatus.ASSIGNED);
        complaint.changeStatus(ComplaintStatus.IN_PROGRESS);
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(assignmentRepository.existsByComplaintAndWorkerAndActiveTrue(complaint, worker)).thenReturn(true);

        service.markResolved(complaintId, "Replaced the bulb", worker);

        assertEquals(ComplaintStatus.RESOLVED, complaint.getStatus());
        verify(historyRepository).save(any(StatusHistory.class));
    }

    @Test
    void workerCannotTouchComplaintAssignedToSomeoneElse() {
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(assignmentRepository.existsByComplaintAndWorkerAndActiveTrue(complaint, worker)).thenReturn(false);

        assertThrows(ForbiddenOperationException.class,
                () -> service.markResolved(complaintId, null, worker));
    }

    @Test
    void citizenCannotViewAnotherCitizensComplaint() {
        User other = userWithId("Other", "other@example.com", Role.CITIZEN);
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));

        assertThrows(ForbiddenOperationException.class, () -> service.getForViewer(complaintId, other));
    }

    @Test
    void citizenCanViewOwnComplaintAndAuthorityCanViewAny() {
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));

        assertSame(complaint, service.getForViewer(complaintId, citizen));
        assertSame(complaint, service.getForViewer(complaintId, authority));
    }
}
