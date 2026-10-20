package com.civictrack.service.impl;

import com.civictrack.dto.ComplaintFilter;
import com.civictrack.dto.ComplaintForm;
import com.civictrack.entity.Assignment;
import com.civictrack.entity.Category;
import com.civictrack.entity.Complaint;
import com.civictrack.entity.StatusHistory;
import com.civictrack.entity.User;
import com.civictrack.enums.ComplaintStatus;
import com.civictrack.enums.Priority;
import com.civictrack.enums.Role;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.exception.ForbiddenOperationException;
import com.civictrack.exception.ResourceNotFoundException;
import com.civictrack.repository.AssignmentRepository;
import com.civictrack.repository.ComplaintRepository;
import com.civictrack.repository.ComplaintSpecs;
import com.civictrack.repository.StatusHistoryRepository;
import com.civictrack.service.CategoryService;
import com.civictrack.service.ComplaintService;
import com.civictrack.service.FileStorageService;
import com.civictrack.service.NotificationService;
import com.civictrack.service.UserService;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The heart of CivicTrack: all complaint business rules live here.
 * COMPOSITION: this class does little itself; it coordinates repositories and other services.
 */
@Service
@Transactional
public class ComplaintServiceImpl implements ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final AssignmentRepository assignmentRepository;
    private final StatusHistoryRepository historyRepository;
    private final CategoryService categoryService;
    private final UserService userService;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService; // interface: any implementation works

    public ComplaintServiceImpl(ComplaintRepository complaintRepository,
                                AssignmentRepository assignmentRepository,
                                StatusHistoryRepository historyRepository,
                                CategoryService categoryService,
                                UserService userService,
                                FileStorageService fileStorageService,
                                NotificationService notificationService) {
        this.complaintRepository = complaintRepository;
        this.assignmentRepository = assignmentRepository;
        this.historyRepository = historyRepository;
        this.categoryService = categoryService;
        this.userService = userService;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
    }

    // ------------------------------------------------------------------ citizen

    @Override
    public Complaint submit(ComplaintForm form, MultipartFile image, User citizen) {
        requireRole(citizen, Role.CITIZEN);
        Category category = categoryService.getActiveById(form.getCategoryId());
        Complaint complaint = new Complaint(form.getTitle(), form.getDescription(),
                form.getLocation(), category, citizen);
        if (image != null && !image.isEmpty()) {
            complaint.attachImage(fileStorageService.store(image));
        }
        complaintRepository.save(complaint);
        recordHistory(complaint, null, ComplaintStatus.SUBMITTED, citizen, "Complaint submitted");
        return complaint;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Complaint> findForCitizen(User citizen) {
        return complaintRepository.findByCitizenOrderByCreatedAtDesc(citizen);
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
        StringBuilder csv = new StringBuilder("Title,Category,Status,Priority,Location,Submitted\n");
        for (Complaint c : rows) {
            csv.append(csvCell(c.getTitle())).append(',')
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
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    @Override
    public void startReview(UUID id, User actor) {
        requireRole(actor, Role.AUTHORITY);
        Complaint complaint = findComplaint(id);
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
            throw new BusinessRuleException("The priority of a closed or rejected complaint cannot be changed.");
        }
        complaint.changePriority(priority);
        complaintRepository.save(complaint);
        recordHistory(complaint, complaint.getStatus(), complaint.getStatus(), actor,
                "Priority set to " + priority.getLabel());
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
        assignmentRepository.findByComplaintAndActiveTrue(complaint).ifPresent(old -> {
            old.deactivate();
            assignmentRepository.saveAndFlush(old);
        });
        assignmentRepository.save(new Assignment(complaint, worker, actor, notes));

        if (current == ComplaintStatus.UNDER_REVIEW) {
            transition(complaint, ComplaintStatus.ASSIGNED, actor, "Assigned to " + worker.getFullName());
        } else {
            recordHistory(complaint, current, current, actor, "Re-assigned to " + worker.getFullName());
        }
        notificationService.notifyUser(worker, "New assignment: " + complaint.getTitle(),
                "A complaint has been assigned to you. Please log in to CivicTrack to view it.");
    }

    @Override
    public void close(UUID id, String notes, User actor) {
        requireRole(actor, Role.AUTHORITY);
        Complaint complaint = findComplaint(id);
        transition(complaint, ComplaintStatus.CLOSED, actor, blankToDefault(notes, "Resolution verified and complaint closed"));
    }

    @Override
    public void sendBackToWork(UUID id, String notes, User actor) {
        requireRole(actor, Role.AUTHORITY);
        if (notes == null || notes.isBlank()) {
            throw new BusinessRuleException("Please explain what still needs to be fixed.");
        }
        Complaint complaint = findComplaint(id);
        transition(complaint, ComplaintStatus.IN_PROGRESS, actor, "Sent back to worker: " + notes.trim());
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
    public void addNote(UUID id, String note, User actor) {
        Complaint complaint = findAssignedComplaint(id, actor);
        if (note == null || note.isBlank()) {
            throw new BusinessRuleException("Please write a progress note.");
        }
        ComplaintStatus status = complaint.getStatus();
        if (status != ComplaintStatus.ASSIGNED && status != ComplaintStatus.IN_PROGRESS) {
            throw new BusinessRuleException("Notes can only be added while the work is Assigned or In Progress.");
        }
        recordHistory(complaint, status, status, actor, note.trim());
    }

    @Override
    public void markResolved(UUID id, String note, User actor) {
        Complaint complaint = findAssignedComplaint(id, actor);
        transition(complaint, ComplaintStatus.RESOLVED, actor,
                blankToDefault(note, "Work completed - awaiting authority verification"));
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
        notificationService.notifyUser(complaint.getCitizen(),
                "Update on your complaint: " + complaint.getTitle(),
                "Your complaint status is now: " + complaint.getStatus().getLabel() + ".");
    }

    private void recordHistory(Complaint complaint, ComplaintStatus old, ComplaintStatus next,
                               User actor, String notes) {
        historyRepository.save(new StatusHistory(complaint, old, next, actor, notes));
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
