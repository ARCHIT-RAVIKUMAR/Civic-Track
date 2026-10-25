package com.civictrack.repository;

import com.civictrack.dto.ComplaintFilter;
import com.civictrack.entity.Category;
import com.civictrack.entity.Complaint;
import com.civictrack.entity.User;
import com.civictrack.enums.ComplaintStatus;
import com.civictrack.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
@ActiveProfiles("test")
class ComplaintRepositoryTest {

    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private jakarta.persistence.EntityManager em;

    private User citizen;
    private Category roads;
    private Category garbage;

    @BeforeEach
    void setUp() {
        citizen = userRepository.save(new User("Asha", "asha@example.com", "hash", Role.CITIZEN));
        roads = categoryRepository.save(new Category("Roads", null));
        garbage = categoryRepository.save(new Category("Garbage", null));

        complaintRepository.save(new Complaint("Pothole one", "Large pothole on main road", "Main Rd", roads, citizen));
        Complaint second = new Complaint("Garbage pile", "Garbage not collected for days", "Park St", garbage, citizen);
        second.changeStatus(ComplaintStatus.UNDER_REVIEW);
        complaintRepository.save(second);
    }

    @Test
    void savedEntitiesGetIdAndTimestamps() {
        Complaint c = complaintRepository.findByCitizenOrderByCreatedAtDesc(citizen).get(0);
        assertNotNull(c.getId());
        assertNotNull(c.getCreatedAt());
        assertNotNull(c.getUpdatedAt());
    }

    @Test
    void findsOnlyTheCitizensOwnComplaints() {
        assertEquals(2, complaintRepository.findByCitizenOrderByCreatedAtDesc(citizen).size());
        User other = userRepository.save(new User("Ravi", "ravi@example.com", "hash", Role.CITIZEN));
        assertEquals(0, complaintRepository.findByCitizenOrderByCreatedAtDesc(other).size());
    }

    @Test
    void filtersByStatus() {
        ComplaintFilter filter = new ComplaintFilter();
        filter.setStatus(ComplaintStatus.UNDER_REVIEW);
        var page = complaintRepository.findAll(ComplaintSpecs.withFilter(filter), PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertEquals("Garbage pile", page.getContent().get(0).getTitle());
    }

    @Test
    void filtersByCategory() {
        ComplaintFilter filter = new ComplaintFilter();
        filter.setCategoryId(roads.getId());
        var page = complaintRepository.findAll(ComplaintSpecs.withFilter(filter), PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
    }

    @Test
    void emptyFilterReturnsEverything() {
        var page = complaintRepository.findAll(ComplaintSpecs.withFilter(new ComplaintFilter()), PageRequest.of(0, 10));
        assertEquals(2, page.getTotalElements());
    }

    @Test
    void keywordSearchMatchesTitleDescriptionAndLocationIgnoringCase() {
        ComplaintFilter byTitle = new ComplaintFilter();
        byTitle.setQ("POTHOLE");
        assertEquals(1, complaintRepository.findAll(ComplaintSpecs.withFilter(byTitle), PageRequest.of(0, 10)).getTotalElements());

        ComplaintFilter byLocation = new ComplaintFilter();
        byLocation.setQ("park st");
        assertEquals(1, complaintRepository.findAll(ComplaintSpecs.withFilter(byLocation), PageRequest.of(0, 10)).getTotalElements());

        ComplaintFilter byDescription = new ComplaintFilter();
        byDescription.setQ("not collected");
        assertEquals(1, complaintRepository.findAll(ComplaintSpecs.withFilter(byDescription), PageRequest.of(0, 10)).getTotalElements());

        ComplaintFilter none = new ComplaintFilter();
        none.setQ("zzzz");
        assertEquals(0, complaintRepository.findAll(ComplaintSpecs.withFilter(none), PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void searchTreatsPercentSignAsPlainText() {
        ComplaintFilter filter = new ComplaintFilter();
        filter.setQ("%");
        assertEquals(0, complaintRepository.findAll(ComplaintSpecs.withFilter(filter), PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void countsComplaintsPerCategory() {
        complaintRepository.save(new Complaint("Pothole two", "Another large pothole nearby", "Main Rd", roads, citizen));
        var rows = complaintRepository.countByCategory();
        assertEquals(2, rows.size());
        assertEquals("Roads", rows.get(0)[0]);
        assertEquals(2L, rows.get(0)[1]);
    }

    @Test
    void overdueFilterFindsOnlyOldOpenComplaints() {
        Complaint old = complaintRepository.findByCitizenOrderByCreatedAtDesc(citizen).stream()
                .filter(c -> c.getTitle().equals("Pothole one")).findFirst().orElseThrow();
        em.flush();
        em.createNativeQuery("update complaints set created_at = :t where id = :id")
                .setParameter("t", java.time.LocalDateTime.now().minusDays(10))   // MEDIUM target = 7 days
                .setParameter("id", old.getId())
                .executeUpdate();
        em.clear();

        ComplaintFilter filter = new ComplaintFilter();
        filter.setOverdue(true);
        var page = complaintRepository.findAll(ComplaintSpecs.withFilter(filter), PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("Pothole one", page.getContent().get(0).getTitle());
        assertEquals(1L, complaintRepository.count(ComplaintSpecs.withFilter(filter)));
    }

    @Test
    void complaintCanBeFoundByReferenceNumberAndSearchedByIt() {
        Complaint any = complaintRepository.findByCitizenOrderByCreatedAtDesc(citizen).get(0);
        String reference = any.getReferenceNumber();

        assertEquals(any.getId(), complaintRepository.findByReferenceNumber(reference).orElseThrow().getId());

        ComplaintFilter filter = new ComplaintFilter();
        filter.setQ(reference.toLowerCase());
        var page = complaintRepository.findAll(ComplaintSpecs.withFilter(filter), PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
    }

    @Test
    void mapQueryReturnsOnlyComplaintsWithAPin() {
        Complaint pinned = complaintRepository.findByCitizenOrderByCreatedAtDesc(citizen).get(0);
        pinned.locate(9.93, 76.26);
        complaintRepository.save(pinned);

        var found = complaintRepository.findAll(
                ComplaintSpecs.withFilter(new ComplaintFilter()).and(ComplaintSpecs.withCoordinates()));

        assertEquals(1, found.size());
        assertEquals(pinned.getId(), found.get(0).getId());
    }
}
