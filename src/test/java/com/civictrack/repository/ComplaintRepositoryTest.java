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
}
