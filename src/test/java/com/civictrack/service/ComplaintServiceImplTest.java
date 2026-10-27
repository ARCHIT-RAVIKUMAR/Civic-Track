package com.civictrack.service;

import com.civictrack.dto.ComplaintFilter;
import com.civictrack.dto.ComplaintForm;
import com.civictrack.entity.Assignment;
import com.civictrack.entity.Category;
import com.civictrack.entity.Complaint;
import com.civictrack.entity.ComplaintComment;
import com.civictrack.entity.StatusHistory;
import com.civictrack.entity.User;
import com.civictrack.enums.ComplaintStatus;
import com.civictrack.enums.Role;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.exception.ForbiddenOperationException;
import com.civictrack.exception.InvalidStatusTransitionException;
import com.civictrack.repository.AssignmentRepository;
import com.civictrack.repository.ComplaintCommentRepository;
import com.civictrack.repository.ComplaintRepository;
import com.civictrack.repository.StatusHistoryRepository;
import com.civictrack.service.impl.ComplaintServiceImpl;
import com.civictrack.enums.Priority;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintServiceImplTest {

    @Mock private ComplaintRepository complaintRepository;
    @Mock private AssignmentRepository assignmentRepository;
    @Mock private StatusHistoryRepository historyRepository;
    @Mock private ComplaintCommentRepository commentRepository;
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
        service = new ComplaintServiceImpl(complaintRepository, assignmentRepository, historyRepository, commentRepository,
                categoryService, userService, fileStorageService, List.of(notificationService));
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
        verify(notificationService).notifyUser(eq(worker), anyString(), anyString(), any());
        verify(notificationService).notifyUser(eq(citizen), anyString(), anyString(), any());
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

    // ---------------------------------------------------------------- new features

    private ComplaintForm form(UUID categoryId, String location) {
        ComplaintForm f = new ComplaintForm();
        f.setTitle("Pothole on road");
        f.setDescription("A deep pothole that is dangerous");
        f.setLocation(location);
        f.setCategoryId(categoryId);
        return f;
    }

    @Test
    void submittingAComplaintNotifiesAuthoritiesAndAdmins() {
        User admin = userWithId("Admin", "admin@example.com", Role.ADMIN);
        Category category = new Category("Roads", null);
        UUID categoryId = UUID.randomUUID();
        when(categoryService.getActiveById(categoryId)).thenReturn(category);
        when(userService.findActiveByRole(Role.AUTHORITY)).thenReturn(List.of(authority));
        when(userService.findActiveByRole(Role.ADMIN)).thenReturn(List.of(admin));

        Complaint saved = service.submit(form(categoryId, "MG Road"), null, citizen);

        assertEquals(ComplaintStatus.SUBMITTED, saved.getStatus());
        verify(notificationService).notifyUser(eq(authority), anyString(), anyString(), any());
        verify(notificationService).notifyUser(eq(admin), anyString(), anyString(), any());
    }

    @Test
    void workerMarkingResolvedNotifiesAuthorities() {
        complaint.changeStatus(ComplaintStatus.UNDER_REVIEW);
        complaint.changeStatus(ComplaintStatus.ASSIGNED);
        complaint.changeStatus(ComplaintStatus.IN_PROGRESS);
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(assignmentRepository.existsByComplaintAndWorkerAndActiveTrue(complaint, worker)).thenReturn(true);
        when(userService.findActiveByRole(Role.AUTHORITY)).thenReturn(List.of(authority));

        service.markResolved(complaintId, "Done", worker);

        verify(notificationService).notifyUser(eq(authority), anyString(), anyString(), any());
    }

    @Test
    void citizenCanWithdrawOwnComplaintBeforeAssignment() {
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));

        service.withdraw(complaintId, citizen);

        assertEquals(ComplaintStatus.WITHDRAWN, complaint.getStatus());
        verify(historyRepository).save(any(StatusHistory.class));
    }

    @Test
    void withdrawIsRefusedOnceAssigned() {
        complaint.changeStatus(ComplaintStatus.UNDER_REVIEW);
        complaint.changeStatus(ComplaintStatus.ASSIGNED);
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));

        assertThrows(InvalidStatusTransitionException.class, () -> service.withdraw(complaintId, citizen));
        assertEquals(ComplaintStatus.ASSIGNED, complaint.getStatus());
    }

    @Test
    void citizenCannotWithdrawSomeoneElsesComplaint() {
        User other = userWithId("Other", "other@example.com", Role.CITIZEN);
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));

        assertThrows(ForbiddenOperationException.class, () -> service.withdraw(complaintId, other));
    }

    @Test
    void similarOpenComplaintIsReportedAsPossibleDuplicate() {
        Category category = new Category("Streetlight", null);
        UUID categoryId = UUID.randomUUID();
        Complaint existing = new Complaint("Light out", "No light for days here", "5th Cross, near temple",
                category, userWithId("Neighbour", "n@example.com", Role.CITIZEN));
        when(categoryService.getActiveById(categoryId)).thenReturn(category);
        when(complaintRepository.findByCategoryAndStatusInAndCreatedAtAfter(eq(category), any(), any()))
                .thenReturn(List.of(existing));

        List<Complaint> duplicates = service.findPossibleDuplicates(form(categoryId, "5th cross"), citizen);

        assertEquals(1, duplicates.size());
    }

    @Test
    void differentLocationIsNotADuplicate() {
        Category category = new Category("Streetlight", null);
        UUID categoryId = UUID.randomUUID();
        Complaint existing = new Complaint("Light out", "No light for days here", "Gandhi Nagar bus stand",
                category, userWithId("Neighbour", "n@example.com", Role.CITIZEN));
        when(categoryService.getActiveById(categoryId)).thenReturn(category);
        when(complaintRepository.findByCategoryAndStatusInAndCreatedAtAfter(eq(category), any(), any()))
                .thenReturn(List.of(existing));

        assertTrue(service.findPossibleDuplicates(form(categoryId, "Park Avenue"), citizen).isEmpty());
    }

    // ---------------------------------------------------------------- comments, audit, map

    @Test
    void citizenCommentNotifiesAuthorities() {
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(userService.findActiveByRole(Role.AUTHORITY)).thenReturn(List.of(authority));

        service.addComment(complaintId, "  The pothole is getting bigger  ", citizen);

        verify(commentRepository).save(any(ComplaintComment.class));
        verify(notificationService).notifyUser(eq(authority), anyString(), anyString(), any());
    }

    @Test
    void authorityCommentNotifiesTheCitizen() {
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));

        service.addComment(complaintId, "Please send a clearer photo", authority);

        verify(commentRepository).save(any(ComplaintComment.class));
        verify(notificationService).notifyUser(eq(citizen), anyString(), anyString(), any());
    }

    @Test
    void administratorsCannotComment() {
        User admin = userWithId("Admin", "admin@example.com", Role.ADMIN);

        assertThrows(ForbiddenOperationException.class, () -> service.addComment(complaintId, "Hello", admin));
    }

    @Test
    void otherCitizensCannotCommentOnSomeoneElsesComplaint() {
        User other = userWithId("Other", "other@example.com", Role.CITIZEN);
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));

        assertThrows(ForbiddenOperationException.class, () -> service.addComment(complaintId, "Hi", other));
    }

    @Test
    void blankCommentIsRejected() {
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));

        assertThrows(BusinessRuleException.class, () -> service.addComment(complaintId, "   ", citizen));
    }

    @Test
    void commentsAreClosedOnFinishedComplaints() {
        complaint.reject("Duplicate");
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));

        assertThrows(BusinessRuleException.class, () -> service.addComment(complaintId, "Hello", citizen));
    }

    @Test
    void onlyAdministratorsCanReadTheAuditLog() {
        assertThrows(ForbiddenOperationException.class, () -> service.findAuditLog(0, 10, authority));
    }

    @Test
    void submittedComplaintKeepsItsMapPosition() {
        Category category = new Category("Roads", null);
        UUID categoryId = UUID.randomUUID();
        when(categoryService.getActiveById(categoryId)).thenReturn(category);
        ComplaintForm f = form(categoryId, "MG Road");
        f.setLatitude(9.9312);
        f.setLongitude(76.2673);

        Complaint saved = service.submit(f, null, citizen);

        assertTrue(saved.hasCoordinates());
        assertEquals(9.9312, saved.getLatitude());
    }

    // ---------------------------------------------------------------- reopen, exports, guards

    private void closeComplaint() {
        complaint.changeStatus(ComplaintStatus.UNDER_REVIEW);
        complaint.changeStatus(ComplaintStatus.ASSIGNED);
        complaint.changeStatus(ComplaintStatus.IN_PROGRESS);
        complaint.changeStatus(ComplaintStatus.RESOLVED);
        complaint.changeStatus(ComplaintStatus.CLOSED);
    }

    private StatusHistory closedEntry(LocalDateTime when) {
        StatusHistory entry = new StatusHistory(complaint, ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED, authority, null);
        ReflectionTestUtils.setField(entry, "createdAt", when);
        return entry;
    }

    @Test
    void citizenCanReopenARecentlyClosedComplaint() {
        closeComplaint();
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(historyRepository.findByComplaintOrderByCreatedAtAsc(complaint))
                .thenReturn(List.of(closedEntry(LocalDateTime.now().minusDays(2))));
        when(userService.findActiveByRole(Role.AUTHORITY)).thenReturn(List.of(authority));

        service.reopen(complaintId, "The pothole is back", citizen);

        assertEquals(ComplaintStatus.UNDER_REVIEW, complaint.getStatus());
        verify(notificationService).notifyUser(eq(authority), anyString(), anyString(), any());
    }

    @Test
    void reopenIsRefusedAfterTheWindow() {
        closeComplaint();
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(historyRepository.findByComplaintOrderByCreatedAtAsc(complaint))
                .thenReturn(List.of(closedEntry(LocalDateTime.now().minusDays(20))));

        assertThrows(BusinessRuleException.class, () -> service.reopen(complaintId, "Still broken", citizen));
        assertEquals(ComplaintStatus.CLOSED, complaint.getStatus());
    }

    @Test
    void onlyClosedComplaintsCanBeReopened() {
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));

        assertThrows(BusinessRuleException.class, () -> service.reopen(complaintId, "Hello", citizen));
    }

    @Test
    void otherCitizensCannotReopen() {
        User other = userWithId("Other", "other@example.com", Role.CITIZEN);
        closeComplaint();
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));

        assertThrows(ForbiddenOperationException.class, () -> service.reopen(complaintId, "Hello", other));
    }

    @Test
    void reopenNeedsAReason() {
        closeComplaint();
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(historyRepository.findByComplaintOrderByCreatedAtAsc(complaint))
                .thenReturn(List.of(closedEntry(LocalDateTime.now().minusDays(1))));

        assertThrows(BusinessRuleException.class, () -> service.reopen(complaintId, "  ", citizen));
    }

    @Test
    void canReopenOnlyForTheOwnerWithinTheWindow() {
        closeComplaint();
        when(historyRepository.findByComplaintOrderByCreatedAtAsc(complaint))
                .thenReturn(List.of(closedEntry(LocalDateTime.now().minusDays(1))));

        assertTrue(service.canReopen(complaint, citizen));
        assertFalse(service.canReopen(complaint, authority));
    }

    @Test
    void startReviewOnlyWorksOnNewComplaints() {
        complaint.changeStatus(ComplaintStatus.UNDER_REVIEW);
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));

        assertThrows(BusinessRuleException.class, () -> service.startReview(complaintId, authority));
    }

    @Test
    void onlyAdministratorsCanExportTheAuditLog() {
        assertThrows(ForbiddenOperationException.class, () -> service.exportAuditCsv(authority));
    }

    // ---------------------------------------------------------------- tracking and map

    @Test
    void trackingShowsStatusStepsButNotPrivateNotes() {
        StatusHistory submitted = new StatusHistory(complaint, null, ComplaintStatus.SUBMITTED, citizen, "Complaint submitted");
        StatusHistory review = new StatusHistory(complaint, ComplaintStatus.SUBMITTED, ComplaintStatus.UNDER_REVIEW, authority, "Review started");
        StatusHistory note = new StatusHistory(complaint, ComplaintStatus.UNDER_REVIEW, ComplaintStatus.UNDER_REVIEW, authority, "Priority set to High");
        when(complaintRepository.findByReferenceNumber(complaint.getReferenceNumber())).thenReturn(Optional.of(complaint));
        when(historyRepository.findByComplaintOrderByCreatedAtAsc(complaint)).thenReturn(List.of(submitted, review, note));

        var info = service.track(" " + complaint.getReferenceNumber().toLowerCase() + " ").orElseThrow();

        assertEquals(complaint.getReferenceNumber(), info.getReferenceNumber());
        assertEquals(2, info.getSteps().size());          // the priority note is not shown
        assertEquals("Streetlight", info.getCategoryName());
    }

    @Test
    void trackingReturnsNothingForUnknownOrMalformedReferences() {
        when(complaintRepository.findByReferenceNumber("CT-AAAA-BBBB")).thenReturn(Optional.empty());

        assertTrue(service.track("CT-AAAA-BBBB").isEmpty());
        assertTrue(service.track("nonsense").isEmpty());
        assertTrue(service.track(null).isEmpty());
    }

    @Test
    void onlyAuthoritiesCanUseTheMapQuery() {
        assertThrows(ForbiddenOperationException.class, () -> service.findForMap(new ComplaintFilter(), citizen));
    }

    // ---------------------------------------------------------------- who gets told what

    private void toInProgress() {
        complaint.changeStatus(ComplaintStatus.UNDER_REVIEW);
        complaint.changeStatus(ComplaintStatus.ASSIGNED);
        complaint.changeStatus(ComplaintStatus.IN_PROGRESS);
    }

    @Test
    void reassignmentSendsInstructionsToTheNewWorkerAndTellsTheOldOne() {
        User newWorker = userWithId("New Worker", "new@example.com", Role.FIELD_WORKER);
        complaint.changeStatus(ComplaintStatus.UNDER_REVIEW);
        complaint.changeStatus(ComplaintStatus.ASSIGNED);
        Assignment old = new Assignment(complaint, worker, authority, null);
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(userService.getById(newWorker.getId())).thenReturn(newWorker);
        when(assignmentRepository.findByComplaintAndActiveTrue(complaint)).thenReturn(Optional.of(old));

        service.assign(complaintId, newWorker.getId(), "Bring a ladder", authority);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyUser(eq(newWorker), anyString(), message.capture(), any());
        assertTrue(message.getValue().contains("Bring a ladder"));
        verify(notificationService).notifyUser(eq(worker), anyString(), anyString(), any());
    }

    @Test
    void workerNoteTellsTheCitizenButOnlyAlertsOfficersWhenAsked() {
        toInProgress();
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(assignmentRepository.existsByComplaintAndWorkerAndActiveTrue(complaint, worker)).thenReturn(true);
        when(userService.findActiveByRole(Role.AUTHORITY)).thenReturn(List.of(authority));

        service.addNote(complaintId, "Waiting for materials", false, worker);

        verify(notificationService).notifyUser(eq(citizen), anyString(), anyString(), any());
        verify(notificationService, never()).notifyUser(eq(authority), anyString(), anyString(), any());

        service.addNote(complaintId, "The road is blocked", true, worker);

        verify(notificationService).notifyUser(eq(authority), anyString(), anyString(), any());
    }

    @Test
    void priorityChangeIsSentToTheAssignedWorker() {
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(assignmentRepository.findByComplaintAndActiveTrue(complaint))
                .thenReturn(Optional.of(new Assignment(complaint, worker, authority, null)));

        service.changePriority(complaintId, Priority.URGENT, authority);

        verify(notificationService).notifyUser(eq(worker), anyString(), anyString(), any());
    }

    @Test
    void closingTellsBothTheCitizenAndTheWorker() {
        toInProgress();
        complaint.changeStatus(ComplaintStatus.RESOLVED);
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(assignmentRepository.findByComplaintAndActiveTrue(complaint))
                .thenReturn(Optional.of(new Assignment(complaint, worker, authority, null)));

        service.close(complaintId, "Looks good", authority);

        verify(notificationService).notifyUser(eq(citizen), anyString(), anyString(), any());
        verify(notificationService).notifyUser(eq(worker), anyString(), anyString(), any());
    }

    @Test
    void rejectionReasonReachesTheCitizen() {
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));

        service.reject(complaintId, "Duplicate of an earlier report", authority);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyUser(eq(citizen), anyString(), message.capture(), any());
        assertTrue(message.getValue().contains("Duplicate of an earlier report"));
    }

    @Test
    void reopeningTellsTheWorkerWhoWasReleased() {
        closeComplaint();
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(historyRepository.findByComplaintOrderByCreatedAtAsc(complaint))
                .thenReturn(List.of(closedEntry(LocalDateTime.now().minusDays(1))));
        when(assignmentRepository.findByComplaintAndActiveTrue(complaint))
                .thenReturn(Optional.of(new Assignment(complaint, worker, authority, null)));
        when(userService.findActiveByRole(Role.AUTHORITY)).thenReturn(List.of(authority));

        service.reopen(complaintId, "It broke again", citizen);

        verify(notificationService).notifyUser(eq(worker), anyString(), anyString(), any());
        verify(notificationService).notifyUser(eq(authority), anyString(), anyString(), any());
    }

    @Test
    void workerCommentGoesToTheCitizenAndOfficersButNotBackToTheWorker() {
        toInProgress();
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(assignmentRepository.existsByComplaintAndWorkerAndActiveTrue(complaint, worker)).thenReturn(true);
        when(assignmentRepository.findByComplaintAndActiveTrue(complaint))
                .thenReturn(Optional.of(new Assignment(complaint, worker, authority, null)));
        when(userService.findActiveByRole(Role.AUTHORITY)).thenReturn(List.of(authority));

        service.addComment(complaintId, "Arriving tomorrow", worker);

        verify(notificationService).notifyUser(eq(citizen), anyString(), anyString(), any());
        verify(notificationService).notifyUser(eq(authority), anyString(), anyString(), any());
        verify(notificationService, never()).notifyUser(eq(worker), anyString(), anyString(), any());
    }

    @Test
    void authorityCommentReachesTheCitizenAndTheAssignedWorker() {
        toInProgress();
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(assignmentRepository.findByComplaintAndActiveTrue(complaint))
                .thenReturn(Optional.of(new Assignment(complaint, worker, authority, null)));

        service.addComment(complaintId, "Please send a photo when done", authority);

        verify(notificationService).notifyUser(eq(citizen), anyString(), anyString(), any());
        verify(notificationService).notifyUser(eq(worker), anyString(), anyString(), any());
    }
}
