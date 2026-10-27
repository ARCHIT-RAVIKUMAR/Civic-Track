package com.civictrack.service.impl;

import com.civictrack.dto.ComplaintFilter;
import com.civictrack.dto.ComplaintForm;
import com.civictrack.entity.Assignment;
import com.civictrack.entity.Category;
import com.civictrack.entity.Complaint;
import com.civictrack.entity.ComplaintComment;
import com.civictrack.entity.StatusHistory;
import com.civictrack.entity.User;
import com.civictrack.enums.ComplaintStatus;
import com.civictrack.enums.Priority;
import com.civictrack.enums.Role;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.exception.ForbiddenOperationException;
import com.civictrack.exception.ResourceNotFoundException;
import com.civictrack.repository.AssignmentRepository;
import com.civictrack.repository.ComplaintCommentRepository;
import com.civictrack.repository.ComplaintRepository;
import com.civictrack.repository.ComplaintSpecs;
import com.civictrack.repository.StatusHistoryRepository;
import com.civictrack.service.CategoryService;
import com.civictrack.service.ComplaintService;
import com.civictrack.service.FileStorageService;
import com.civictrack.service.NotificationService;
import com.civictrack.service.TrackingInfo;
import com.civictrack.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The heart of CivicTrack: all complaint business rules live here.
 * COMPOSITION: this class does little itself; it coordinates repositories and other services.
 * POLYMORPHISM: it receives EVERY NotificationService implementation (in-app, log, e-mail ...)
 * and calls them all the same way.
 */
@Service
@Transactional
public class ComplaintServiceImpl implements ComplaintService {

    private static final Logger log = LoggerFactory.getLogger(ComplaintServiceImpl.class);

    private static final int REOPEN_DAYS = 7;

    private static final Set<ComplaintStatus> OPEN_STATUSES = EnumSet.of(
            ComplaintStatus.SUBMITTED, ComplaintStatus.UNDER_REVIEW,
            ComplaintStatus.ASSIGNED, ComplaintStatus.IN_PROGRESS);

    private final ComplaintRepository complaintRepository;
    private final AssignmentRepository assignmentRepository;
    private final StatusHistoryRepository historyRepository;
    private final ComplaintCommentRepository commentRepository;
    private final CategoryService categoryService;
    private final UserService userService;
    private final FileStorageService fileStorageService;
    private final List<NotificationService> notifiers;

    public ComplaintServiceImpl(ComplaintRepository complaintRepository,
                                AssignmentRepository assignmentRepository,
                                StatusHistoryRepository historyRepository,
                                ComplaintCommentRepository commentRepository,
                                CategoryService categoryService,
                                UserService userService,
                                FileStorageService fileStorageService,
                                List<NotificationService> notifiers) {
        this.complaintRepository = complaintRepository;
        this.assignmentRepository = assignmentRepository;
        this.historyRepository = historyRepository;
        this.commentRepository = commentRepository;
        this.categoryService = categoryService;
        this.userService = userService;
        this.fileStorageService = fileStorageService;
        this.notifiers = notifiers;
    }

    // ------------------------------------------------------------------ citizen

    @Override
    public Complaint submit(ComplaintForm form, MultipartFile image, User citizen) {
        requireRole(citizen, Role.CITIZEN);
        Category category = categoryService.getActiveById(form.getCategoryId());
        Complaint complaint = new Complaint(form.getTitle(), form.getDescription(),
                form.getLocation(), category, citizen);
        complaint.locate(form.getLatitude(), form.getLongitude());
        if (image != null && !image.isEmpty()) {
            complaint.attachImage(fileStorageService.store(image));
        }
        complaintRepository.save(complaint);
        recordHistory(complaint, null, ComplaintStatus.SUBMITTED, citizen, "Complaint submitted");

        // Tell the people who now have work to do
        String subject = "New complaint reported: " + complaint.getTitle();
        String message = category.getName() + " at " + complaint.getLocation();
        notifyRole(Role.AUTHORITY, subject, message, complaint);
        notifyRole(Role.ADMIN, subject, message, complaint);
        return complaint;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Complaint> findForCitizen(User citizen, int page, int size) {
        return complaintRepository.findByCitizen(citizen,
                PageRequest.of(Math.max(page, 0), size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Complaint> findPossibleDuplicates(ComplaintForm form, User citizen) {
        requireRole(citizen, Role.CITIZEN);
        Category category = categoryService.getActiveById(form.getCategoryId());
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        return complaintRepository.findByCategoryAndStatusInAndCreatedAtAfter(category, OPEN_STATUSES, since)
                .stream()
                .filter(c -> LocationSimilarity.isSimilar(c.getLocation(), form.getLocation()))
                .limit(3)
                .toList();
    }

    @Override
    public void withdraw(UUID id, User actor) {
        requireRole(actor, Role.CITIZEN);
        Complaint complaint = findComplaint(id);
        if (!complaint.isOwnedBy(actor)) {
            throw new ForbiddenOperationException("You can only withdraw your own complaints.");
        }
        transition(complaint, ComplaintStatus.WITHDRAWN, actor, "Withdrawn by the citizen");
        notifyRole(Role.AUTHORITY, "Complaint withdrawn: " + complaint.getTitle(),
                "The citizen withdrew this complaint. No further action is needed.", complaint);
    }

    // ------------------------------------------------------------------ public tracking

    @Override
    @Transactional(readOnly = true)
    public Optional<TrackingInfo> track(String reference) {
        String canonical = com.civictrack.entity.ReferenceNumbers.normalize(reference);
        if (canonical == null) {
            return Optional.empty();
        }
        return complaintRepository.findByReferenceNumber(canonical).map(c -> {
            List<TrackingInfo.Step> steps = new ArrayList<>();
            for (StatusHistory h : historyRepository.findByComplaintOrderByCreatedAtAsc(c)) {
                if (h.getOldStatus() != null && h.getOldStatus() == h.getNewStatus()) {
                    continue; // notes and priority changes are private
                }
                steps.add(new TrackingInfo.Step(h.getNewStatus(), h.getCreatedAtDisplay()));
            }
            return new TrackingInfo(c.getReferenceNumber(), c.getCategory().getName(), c.getStatus(),
                    c.getCreatedAtDisplay(), c.getUpdatedAtDisplay(), steps);
        });
    }

    // ------------------------------------------------------------------ viewing

    @Override
    @Transactional(readOnly = true)
    public Complaint getForViewer(UUID complaintId, User viewer) {
        Complaint complaint = findComplaint(complaintId);
        boolean allowed = switch (viewer.getRole()) {
            case ADMIN, AUTHORITY -> true;
            case CITIZEN -> complaint.isOwnedBy(viewer);
            case FIELD_WORKER -> assignmentRepository.existsByComplaintAndWorkerAndActiveTrue(complaint, viewer);
        };
        if (!allowed) {
            throw new ForbiddenOperationException("You do not have permission to view this complaint.");
        }
        return complaint;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StatusHistory> getHistory(Complaint complaint) {
        return historyRepository.findByComplaintOrderByCreatedAtAsc(complaint);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Assignment> getActiveAssignment(Complaint complaint) {
        return assignmentRepository.findByComplaintAndActiveTrue(complaint);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource loadImage(Complaint complaint) {
        if (!complaint.hasImage()) {
            throw new ResourceNotFoundException("This complaint has no image.");
        }
        return fileStorageService.load(complaint.getImageFilename());
    }

    // ------------------------------------------------------------------ authority

    @Override
    @Transactional(readOnly = true)
    public Page<Complaint> search(ComplaintFilter filter, int page, int size, User actor) {
        requireRole(actor, Role.AUTHORITY);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return complaintRepository.findAll(ComplaintSpecs.withFilter(filter), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public String exportCsv(ComplaintFilter filter, User actor) {
        requireRole(actor, Role.AUTHORITY);
        List<Complaint> rows = complaintRepository.findAll(
                ComplaintSpecs.withFilter(filter), Sort.by(Sort.Direction.DESC, "createdAt"));
        StringBuilder csv = new StringBuilder("Reference,Title,Category,Status,Priority,Location,Submitted\n");
        for (Complaint c : rows) {
            csv.append(csvCell(c.getReferenceNumber())).append(',')
               .append(csvCell(c.getTitle())).append(',')
               .append(csvCell(c.getCategory().getName())).append(',')
               .append(csvCell(c.getStatus().getLabel())).append(',')
               .append(csvCell(c.getPriority().getLabel())).append(',')
               .append(csvCell(c.getLocation())).append(',')
               .append(csvCell(c.getCreatedAtDisplay())).append('\n');
        }
        return csv.toString();
    }

    /** Quotes the cell and neutralises spreadsheet formulas (=, +, -, @) typed by users. */
    private String csvCell(String value) {
        return CsvUtil.cell(value);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Complaint> findForMap(ComplaintFilter filter, User actor) {
        requireRole(actor, Role.AUTHORITY);
        return complaintRepository.findAll(
                ComplaintSpecs.withFilter(filter).and(ComplaintSpecs.withCoordinates()),
                PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
    }

    @Override
    public void startReview(UUID id, User actor) {
        requireRole(actor, Role.AUTHORITY);
        Complaint complaint = findComplaint(id);
        if (complaint.getStatus() != ComplaintStatus.SUBMITTED) {
            throw new BusinessRuleException("Only newly submitted complaints can be moved to review.");
        }
        transition(complaint, ComplaintStatus.UNDER_REVIEW, actor, "Review started");
    }

    @Override
    public void reject(UUID id, String reason, User actor) {
        requireRole(actor, Role.AUTHORITY);
        Complaint complaint = findComplaint(id);
        ComplaintStatus old = complaint.getStatus();
        complaint.reject(reason); // entity validates reason + transition
        afterTransition(complaint, old, actor, "Rejected: " + reason.trim());
    }

    @Override
    public void changePriority(UUID id, Priority priority, User actor) {
        requireRole(actor, Role.AUTHORITY);
        Complaint complaint = findComplaint(id);
        if (complaint.getStatus().isTerminal()) {
            throw new BusinessRuleException("The priority of a closed, rejected or withdrawn complaint cannot be changed.");
        }
        complaint.changePriority(priority);
        complaintRepository.save(complaint);
        recordHistory(complaint, complaint.getStatus(), complaint.getStatus(), actor,
                "Priority set to " + priority.getLabel());
        assignmentRepository.findByComplaintAndActiveTrue(complaint).ifPresent(a ->
                notifyUser(a.getWorker(), "Priority changed: " + complaint.getTitle(),
                        "New priority: " + priority.getLabel(), complaint));
    }

    @Override
    public void assign(UUID id, UUID workerId, String notes, User actor) {
        requireRole(actor, Role.AUTHORITY);
        Complaint complaint = findComplaint(id);
        User worker = userService.getById(workerId);
        if (!worker.hasRole(Role.FIELD_WORKER) || !worker.isEnabled()) {
            throw new BusinessRuleException("The selected user is not an active field worker.");
        }
        ComplaintStatus current = complaint.getStatus();
        if (current != ComplaintStatus.UNDER_REVIEW && current != ComplaintStatus.ASSIGNED) {
            throw new BusinessRuleException(
                    "A complaint can only be assigned while it is Under Review (or re-assigned while Assigned).");
        }

        // Deactivate the previous assignment first (only one active assignment is allowed per complaint)
        Optional<Assignment> previous = assignmentRepository.findByComplaintAndActiveTrue(complaint);
        previous.ifPresent(old -> {
            old.deactivate();
            assignmentRepository.saveAndFlush(old);
        });
        assignmentRepository.save(new Assignment(complaint, worker, actor, notes));

        if (current == ComplaintStatus.UNDER_REVIEW) {
            transition(complaint, ComplaintStatus.ASSIGNED, actor, "Assigned to " + worker.getFullName());
        } else {
            recordHistory(complaint, current, current, actor, "Re-assigned to " + worker.getFullName());
        }
        String instructions = (notes == null || notes.isBlank()) ? "" : " Instructions: " + notes.trim();
        notifyUser(worker, "New assignment: " + complaint.getTitle(),
                "A complaint at " + complaint.getLocation() + " has been assigned to you." + instructions, complaint);
        previous.map(Assignment::getWorker)
                .filter(oldWorker -> !oldWorker.equals(worker))
                .ifPresent(oldWorker -> notifyUser(oldWorker, "No longer assigned: " + complaint.getTitle(),
                        "This complaint was re-assigned to another worker.", complaint));
    }

    @Override
    public void close(UUID id, String notes, User actor) {
        requireRole(actor, Role.AUTHORITY);
        Complaint complaint = findComplaint(id);
        transition(complaint, ComplaintStatus.CLOSED, actor, blankToDefault(notes, "Resolution verified and complaint closed"));
        assignmentRepository.findByComplaintAndActiveTrue(complaint).ifPresent(a ->
                notifyUser(a.getWorker(), "Job verified and closed: " + complaint.getTitle(),
                        "The authority verified your work. Thank you.", complaint));
    }

    @Override
    public void sendBackToWork(UUID id, String notes, User actor) {
        requireRole(actor, Role.AUTHORITY);
        if (notes == null || notes.isBlank()) {
            throw new BusinessRuleException("Please explain what still needs to be fixed.");
        }
        Complaint complaint = findComplaint(id);
        transition(complaint, ComplaintStatus.IN_PROGRESS, actor, "Sent back to worker: " + notes.trim());
        assignmentRepository.findByComplaintAndActiveTrue(complaint).ifPresent(a ->
                notifyUser(a.getWorker(), "Rework requested: " + complaint.getTitle(), notes.trim(), complaint));
    }

    // ------------------------------------------------------------------ field worker

    @Override
    @Transactional(readOnly = true)
    public List<Complaint> findForWorker(User worker) {
        requireRole(worker, Role.FIELD_WORKER);
        return assignmentRepository.findComplaintsForWorker(worker);
    }

    @Override
    public void startWork(UUID id, User actor) {
        Complaint complaint = findAssignedComplaint(id, actor);
        transition(complaint, ComplaintStatus.IN_PROGRESS, actor, "Work started");
    }

    @Override
    public void addNote(UUID id, String note, boolean alertAuthority, User actor) {
        Complaint complaint = findAssignedComplaint(id, actor);
        if (note == null || note.isBlank()) {
            throw new BusinessRuleException("Please write a progress note.");
        }
        ComplaintStatus status = complaint.getStatus();
        if (status != ComplaintStatus.ASSIGNED && status != ComplaintStatus.IN_PROGRESS) {
            throw new BusinessRuleException("Notes can only be added while the work is Assigned or In Progress.");
        }
        String text = note.trim();
        recordHistory(complaint, status, status, actor, text);
        notifyUser(complaint.getCitizen(), "Progress update on your complaint: " + complaint.getTitle(),
                actor.getFullName() + ": " + text, complaint);
        if (alertAuthority) {
            notifyRole(Role.AUTHORITY, "Worker needs attention: " + complaint.getTitle(),
                    actor.getFullName() + ": " + text, complaint);
        }
    }

    @Override
    public void markResolved(UUID id, String note, User actor) {
        Complaint complaint = findAssignedComplaint(id, actor);
        transition(complaint, ComplaintStatus.RESOLVED, actor,
                blankToDefault(note, "Work completed - awaiting authority verification"));
        notifyRole(Role.AUTHORITY, "Ready for verification: " + complaint.getTitle(),
                actor.getFullName() + " marked the work as completed. Please verify and close it.", complaint);
    }

    // ------------------------------------------------------------------ reopen, overdue, exports

    @Override
    public void reopen(UUID id, String reason, User actor) {
        requireRole(actor, Role.CITIZEN);
        Complaint complaint = findComplaint(id);
        if (!complaint.isOwnedBy(actor)) {
            throw new ForbiddenOperationException("You can only reopen your own complaints.");
        }
        if (complaint.getStatus() != ComplaintStatus.CLOSED) {
            throw new BusinessRuleException("Only closed complaints can be reopened.");
        }
        if (!withinReopenWindow(complaint)) {
            throw new BusinessRuleException("This complaint was closed more than " + REOPEN_DAYS
                    + " days ago. Please report a new complaint instead.");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("Please explain what is still wrong.");
        }
        String text = reason.trim();
        if (text.length() > 500) {
            throw new BusinessRuleException("The explanation is too long (maximum 500 characters).");
        }
        // the earlier worker is released; the authority assigns the complaint again
        Optional<Assignment> previous = assignmentRepository.findByComplaintAndActiveTrue(complaint);
        previous.ifPresent(a -> {
            a.deactivate();
            assignmentRepository.save(a);
        });
        transition(complaint, ComplaintStatus.UNDER_REVIEW, actor, "Reopened by the citizen: " + text);
        previous.map(Assignment::getWorker).ifPresent(worker ->
                notifyUser(worker, "Complaint reopened: " + complaint.getTitle(),
                        "The citizen reopened this complaint. You will be told if it is assigned to you again.", complaint));
        notifyRole(Role.AUTHORITY, "Complaint reopened: " + complaint.getTitle(), text, complaint);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canReopen(Complaint complaint, User viewer) {
        return viewer.hasRole(Role.CITIZEN)
                && complaint.isOwnedBy(viewer)
                && complaint.getStatus() == ComplaintStatus.CLOSED
                && withinReopenWindow(complaint);
    }

    private boolean withinReopenWindow(Complaint complaint) {
        List<StatusHistory> history = historyRepository.findByComplaintOrderByCreatedAtAsc(complaint);
        for (int i = history.size() - 1; i >= 0; i--) {
            StatusHistory entry = history.get(i);
            if (entry.getNewStatus() == ComplaintStatus.CLOSED && entry.getOldStatus() != ComplaintStatus.CLOSED) {
                return entry.getCreatedAt() != null
                        && entry.getCreatedAt().isAfter(LocalDateTime.now().minusDays(REOPEN_DAYS));
            }
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public long countOverdue() {
        ComplaintFilter filter = new ComplaintFilter();
        filter.setOverdue(true);
        return complaintRepository.count(ComplaintSpecs.withFilter(filter));
    }

    @Override
    @Transactional(readOnly = true)
    public String exportAuditCsv(User actor) {
        requireRole(actor, Role.ADMIN);
        StringBuilder csv = new StringBuilder("When,Who,Role,Complaint,From,To,Notes\n");
        Page<StatusHistory> rows = historyRepository.findAll(
                PageRequest.of(0, 5000, Sort.by(Sort.Direction.DESC, "createdAt")));
        for (StatusHistory h : rows.getContent()) {
            csv.append(CsvUtil.row(
                    h.getCreatedAtDisplay(),
                    h.getChangedBy().getFullName(),
                    h.getChangedBy().getRole().getLabel(),
                    h.getComplaint().getTitle(),
                    h.getOldStatus() == null ? "" : h.getOldStatus().getLabel(),
                    h.getNewStatus().getLabel(),
                    h.getNotes() == null ? "" : h.getNotes()));
        }
        return csv.toString();
    }

    // ------------------------------------------------------------------ comments and audit

    @Override
    @Transactional(readOnly = true)
    public List<ComplaintComment> getComments(Complaint complaint) {
        return commentRepository.findByComplaintOrderByCreatedAtAsc(complaint);
    }

    @Override
    public void addComment(UUID complaintId, String message, User actor) {
        if (actor.hasRole(Role.ADMIN)) {
            throw new ForbiddenOperationException("Administrators have read-only access to complaints.");
        }
        Complaint complaint = getForViewer(complaintId, actor); // also checks that this person may see it
        if (complaint.getStatus().isTerminal()) {
            throw new BusinessRuleException("Comments are closed for this complaint.");
        }
        if (message == null || message.isBlank()) {
            throw new BusinessRuleException("Please write a comment.");
        }
        String text = message.trim();
        if (text.length() > 1000) {
            throw new BusinessRuleException("The comment is too long (maximum 1000 characters).");
        }
        commentRepository.save(new ComplaintComment(complaint, actor, text));

        String subject = "New comment on: " + complaint.getTitle();
        String body = actor.getFullName() + ": " + text;
        if (!complaint.isOwnedBy(actor)) {
            notifyUser(complaint.getCitizen(), subject, body, complaint);
        }
        assignmentRepository.findByComplaintAndActiveTrue(complaint)
                .map(Assignment::getWorker)
                .filter(worker -> !worker.equals(actor))
                .ifPresent(worker -> notifyUser(worker, subject, body, complaint));
        if (!actor.hasRole(Role.AUTHORITY)) {
            notifyRole(Role.AUTHORITY, subject, body, complaint);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StatusHistory> findAuditLog(int page, int size, User actor) {
        requireRole(actor, Role.ADMIN);
        return historyRepository.findAll(
                PageRequest.of(Math.max(page, 0), size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    // ------------------------------------------------------------------ statistics

    @Override
    @Transactional(readOnly = true)
    public Map<ComplaintStatus, Long> countByStatus() {
        Map<ComplaintStatus, Long> counts = new EnumMap<>(ComplaintStatus.class);
        for (ComplaintStatus status : ComplaintStatus.values()) {
            counts.put(status, complaintRepository.countByStatus(status));
        }
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countByCategory() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : complaintRepository.countByCategory()) {
            result.put((String) row[0], (Long) row[1]);
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Complaint> findRecent() {
        return complaintRepository.findTop10ByOrderByCreatedAtDesc();
    }

    // ------------------------------------------------------------------ helpers

    private Complaint findComplaint(UUID id) {
        return complaintRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint not found."));
    }

    /** A field worker may only touch complaints actively assigned to them. */
    private Complaint findAssignedComplaint(UUID id, User actor) {
        requireRole(actor, Role.FIELD_WORKER);
        Complaint complaint = findComplaint(id);
        if (!assignmentRepository.existsByComplaintAndWorkerAndActiveTrue(complaint, actor)) {
            throw new ForbiddenOperationException("This complaint is not assigned to you.");
        }
        return complaint;
    }

    private void requireRole(User actor, Role role) {
        if (actor == null || !actor.hasRole(role)) {
            throw new ForbiddenOperationException("You do not have permission to do this.");
        }
    }

    private void transition(Complaint complaint, ComplaintStatus next, User actor, String notes) {
        ComplaintStatus old = complaint.getStatus();
        complaint.changeStatus(next); // throws InvalidStatusTransitionException when illegal
        afterTransition(complaint, old, actor, notes);
    }

    private void afterTransition(Complaint complaint, ComplaintStatus old, User actor, String notes) {
        complaintRepository.save(complaint);
        recordHistory(complaint, old, complaint.getStatus(), actor, notes);
        String message = "Your complaint status is now: " + complaint.getStatus().getLabel() + ".";
        if (complaint.getStatus() == ComplaintStatus.REJECTED && complaint.getRejectionReason() != null) {
            message += " Reason: " + complaint.getRejectionReason();
        } else if (complaint.getStatus() == ComplaintStatus.CLOSED && notes != null && !notes.isBlank()) {
            message += " " + notes.trim();
        }
        notifyUser(complaint.getCitizen(), "Update on your complaint: " + complaint.getTitle(), message, complaint);
    }

    private void recordHistory(Complaint complaint, ComplaintStatus old, ComplaintStatus next,
                               User actor, String notes) {
        historyRepository.save(new StatusHistory(complaint, old, next, actor, notes));
    }

    /** Sends one message through EVERY notification channel. A broken channel never breaks the workflow. */
    private void notifyUser(User recipient, String subject, String message, Complaint complaint) {
        for (NotificationService notifier : notifiers) {
            try {
                notifier.notifyUser(recipient, subject, message, complaint);
            } catch (RuntimeException ex) {
                log.warn("Notification via {} failed: {}", notifier.getClass().getSimpleName(), ex.getMessage());
            }
        }
    }

    private void notifyRole(Role role, String subject, String message, Complaint complaint) {
        for (User recipient : userService.findActiveByRole(role)) {
            notifyUser(recipient, subject, message, complaint);
        }
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
