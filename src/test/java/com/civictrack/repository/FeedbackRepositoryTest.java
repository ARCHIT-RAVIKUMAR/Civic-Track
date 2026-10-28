package com.civictrack.repository;

import com.civictrack.entity.Category;
import com.civictrack.entity.Complaint;
import com.civictrack.entity.Feedback;
import com.civictrack.entity.User;
import com.civictrack.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DataJpaTest
@ActiveProfiles("test")
class FeedbackRepositoryTest {

    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private FeedbackRepository feedbackRepository;

    @Test
    void ratingStatisticsAreCalculatedByTheDatabase() {
        assertNull(feedbackRepository.averageRating());   // nothing yet

        User citizen = userRepository.save(new User("Asha", "asha@example.com", "hash", Role.CITIZEN));
        Category roads = categoryRepository.save(new Category("Roads", null));
        Category lights = categoryRepository.save(new Category("Lights", null));
        Complaint c1 = complaintRepository.save(new Complaint("Pothole", "A big pothole here", "Main Rd", roads, citizen));
        Complaint c2 = complaintRepository.save(new Complaint("Pothole two", "Another pothole here", "Side Rd", roads, citizen));
        Complaint c3 = complaintRepository.save(new Complaint("Light", "Light is out for days", "5th Cross", lights, citizen));
        feedbackRepository.save(new Feedback(c1, citizen, 5, null));
        feedbackRepository.save(new Feedback(c2, citizen, 3, null));
        feedbackRepository.save(new Feedback(c3, citizen, 2, "slow"));

        assertEquals(3.33, feedbackRepository.averageRating(), 0.01);
        assertEquals(3, feedbackRepository.countByRating().size());

        var byCategory = feedbackRepository.averageByCategory();
        assertEquals(2, byCategory.size());
        assertEquals("Roads", byCategory.get(0)[0]);                 // best first
        assertEquals(4.0, ((Number) byCategory.get(0)[1]).doubleValue(), 0.001);
        assertEquals(2L, ((Number) byCategory.get(0)[2]).longValue());
    }
}
