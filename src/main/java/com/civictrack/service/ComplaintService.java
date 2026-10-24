package com.civictrack.service;

import com.civictrack.dto.ComplaintFilter;
import com.civictrack.dto.ComplaintForm;
import com.civictrack.entity.Assignment;
import com.civictrack.entity.Complaint;
import com.civictrack.entity.ComplaintComment;
import com.civictrack.entity.StatusHistory;
import com.civictrack.entity.User;
import com.civictrack.enums.ComplaintStatus;
import com.civictrack.enums.Priority;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Everything the web layer may ask about complaints. Business rules live in the implementation. */
public interface ComplaintService {

    // citizen
    Complaint submit(ComplaintForm form, MultipartFile image, User citizen);

    Page<Complaint> findForCitizen(User citizen, int page, int size);

    /** Open complaints of the same category with a similar location (reported in the last 30 days). */
    List<Complaint> findPossibleDuplicates(ComplaintForm form, User citizen);

    /** A citizen cancels their own complaint before it is assigned. */
    void withdraw(UUID id, User actor);

    // any role (access is checked inside)
    Complaint getForViewer(UUID complaintId, User viewer);

    List<StatusHistory> getHistory(Complaint complaint);

    Optional<Assignment> getActiveAssignment(Complaint complaint);

    Resource loadImage(Complaint complaint);

    // authority
    Page<Complaint> search(ComplaintFilter filter, int page, int size, User actor);

    String exportCsv(ComplaintFilter filter, User actor);

    void startReview(UUID id, User actor);

    void reject(UUID id, String reason, User actor);

    void changePriority(UUID id, Priority priority, User actor);

    void assign(UUID id, UUID workerId, String notes, User actor);

    void close(UUID id, String notes, User actor);

    void sendBackToWork(UUID id, String notes, User actor);

    // field worker
    List<Complaint> findForWorker(User worker);

    void startWork(UUID id, User actor);

    void addNote(UUID id, String note, User actor);

    void markResolved(UUID id, String note, User actor);

    // comments (citizen, authority and the assigned worker can talk on the complaint)
    List<ComplaintComment> getComments(Complaint complaint);

    void addComment(UUID complaintId, String message, User actor);

    /** A citizen reopens their own closed complaint (within 7 days of closing) because it is not really fixed. */
    void reopen(UUID id, String reason, User actor);

    boolean canReopen(Complaint complaint, User viewer);

    /** Open complaints that are older than the target time for their priority. */
    long countOverdue();

    // admin: complete activity log
    Page<StatusHistory> findAuditLog(int page, int size, User actor);

    /** CSV of the latest 5000 audit entries (admin only). */
    String exportAuditCsv(User actor);

    // statistics
    Map<ComplaintStatus, Long> countByStatus();

    /** Category name -> number of complaints, largest first. */
    Map<String, Long> countByCategory();

    List<Complaint> findRecent();
}
