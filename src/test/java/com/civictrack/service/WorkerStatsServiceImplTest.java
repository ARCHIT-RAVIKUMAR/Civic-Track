package com.civictrack.service;

import com.civictrack.entity.Category;
import com.civictrack.entity.Complaint;
import com.civictrack.entity.Feedback;
import com.civictrack.entity.StatusHistory;
import com.civictrack.entity.User;
import com.civictrack.enums.ComplaintStatus;
import com.civictrack.enums.Role;
import com.civictrack.exception.ForbiddenOperationException;
import com.civictrack.repository.AssignmentRepository;
import com.civictrack.repository.FeedbackRepository;
import com.civictrack.repository.StatusHistoryRepository;
import com.civictrack.repository.UserRepository;
import com.civictrack.service.impl.WorkerStatsServiceImpl;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerStatsServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private AssignmentRepository assignmentRepository;
    @Mock private StatusHistoryRepository historyRepository;
    @Mock private FeedbackRepository feedbackRepository;

    private WorkerStatsServiceImpl service;
    private User authority;
    private User worker;
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
        service = new WorkerStatsServiceImpl(userRepository, assignmentRepository, historyRepository, feedbackRepository);
        authority = userWithId("Authority", "a@example.com", Role.AUTHORITY);
        worker = userWithId("Worker", "w@example.com", Role.FIELD_WORKER);
        citizen = userWithId("Citizen", "c@example.com", Role.CITIZEN);
    }

    private Complaint complaint() {
        Complaint c = new Complaint("Light out", "No light for days", "5th Cross", new Category("Light", null), citizen);
        ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
        return c;
    }

    @Test
    void calculatesOpenJobsCompletionTimeAndRating() {
        // one open job
        Complaint open = complaint();
        open.changeStatus(ComplaintStatus.UNDER_REVIEW);
        open.changeStatus(ComplaintStatus.ASSIGNED);

        // one finished job: assigned at 09:00, resolved at 12:00 (180 minutes), rated 4
        Complaint done = complaint();
        LocalDateTime nine = LocalDateTime.of(2026, 1, 1, 9, 0);
        StatusHistory assigned = at(new StatusHistory(done, ComplaintStatus.UNDER_REVIEW, ComplaintStatus.ASSIGNED, authority, null), nine);
        StatusHistory resolved = at(new StatusHistory(done, ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED, worker, null), nine.plusHours(3));
        Feedback feedback = new Feedback(done, citizen, 4, "Good");

        when(userRepository.findByRoleOrderByFullNameAsc(Role.FIELD_WORKER)).thenReturn(List.of(worker));
        when(assignmentRepository.findComplaintsForWorker(worker)).thenReturn(List.of(open));
        when(historyRepository.findTop500ByChangedByAndNewStatusOrderByCreatedAtDesc(worker, ComplaintStatus.RESOLVED))
                .thenReturn(List.of(resolved));
        when(historyRepository.findByComplaintOrderByCreatedAtAsc(done)).thenReturn(List.of(assigned, resolved));
        when(feedbackRepository.findByComplaint(done)).thenReturn(Optional.of(feedback));

        WorkerStats stats = service.stats(authority).get(0);

        assertEquals(1, stats.getOpenJobs());
        assertEquals(0, stats.getOverdueJobs());
        assertEquals(1, stats.getCompletedJobs());
        assertEquals(180.0, stats.getAverageMinutes());
        assertEquals("3.0 h", stats.getAverageTimeDisplay());
        assertEquals(4.0, stats.getAverageRating());
        assertEquals("4.0 / 5", stats.getAverageRatingDisplay());
        assertEquals(1, stats.getRatingCount());
    }

    @Test
    void workerWithNoHistoryShowsDashes() {
        when(userRepository.findByRoleOrderByFullNameAsc(Role.FIELD_WORKER)).thenReturn(List.of(worker));
        when(assignmentRepository.findComplaintsForWorker(worker)).thenReturn(List.of());
        when(historyRepository.findTop500ByChangedByAndNewStatusOrderByCreatedAtDesc(worker, ComplaintStatus.RESOLVED))
                .thenReturn(List.of());

        WorkerStats stats = service.stats(authority).get(0);

        assertEquals(0, stats.getCompletedJobs());
        assertNull(stats.getAverageMinutes());
        assertEquals("-", stats.getAverageTimeDisplay());
        assertEquals("-", stats.getAverageRatingDisplay());
    }

    @Test
    void citizensAndWorkersCannotSeeWorkerStatistics() {
        assertThrows(ForbiddenOperationException.class, () -> service.stats(citizen));
        assertThrows(ForbiddenOperationException.class, () -> service.stats(worker));
    }

    @Test
    void myStatsIncludesWhatCitizensSaidAndIsOnlyForWorkers() {
        Complaint done = complaint();
        LocalDateTime nine = LocalDateTime.of(2026, 1, 1, 9, 0);
        StatusHistory resolved = at(new StatusHistory(done, ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED, worker, null), nine.plusHours(2));
        when(assignmentRepository.findComplaintsForWorker(worker)).thenReturn(List.of());
        when(historyRepository.findTop500ByChangedByAndNewStatusOrderByCreatedAtDesc(worker, ComplaintStatus.RESOLVED))
                .thenReturn(List.of(resolved));
        when(historyRepository.findByComplaintOrderByCreatedAtAsc(done)).thenReturn(List.of(resolved));
        when(feedbackRepository.findByComplaint(done))
                .thenReturn(Optional.of(new Feedback(done, citizen, 5, "Quick and tidy")));

        WorkerStats mine = service.myStats(worker);

        assertEquals(1, mine.getCompletedJobs());
        assertEquals(1, mine.getRecentFeedback().size());
        assertEquals("Quick and tidy", mine.getRecentFeedback().get(0).getComments());
        assertEquals(done.getReferenceNumber(), mine.getRecentFeedback().get(0).getReferenceNumber());
        assertThrows(ForbiddenOperationException.class, () -> service.myStats(authority));
        assertThrows(ForbiddenOperationException.class, () -> service.myStats(citizen));
    }
}
