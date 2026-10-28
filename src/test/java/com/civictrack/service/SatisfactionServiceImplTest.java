package com.civictrack.service;

import com.civictrack.repository.FeedbackRepository;
import com.civictrack.service.impl.SatisfactionServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SatisfactionServiceImplTest {

    @Mock private FeedbackRepository feedbackRepository;

    @Test
    void buildsAverageDistributionAndCategoryRatings() {
        when(feedbackRepository.averageRating()).thenReturn(4.0);
        when(feedbackRepository.count()).thenReturn(3L);
        when(feedbackRepository.countByRating())
                .thenReturn(List.of(new Object[]{5, 2L}, new Object[]{2, 1L}));
        when(feedbackRepository.averageByCategory())
                .thenReturn(Collections.singletonList(new Object[]{"Roads", 4.5, 2L}));

        SatisfactionSummary summary = new SatisfactionServiceImpl(feedbackRepository).summary();

        assertEquals("4.0", summary.getAverageDisplay());
        assertEquals(4, summary.getRoundedStars());
        assertEquals(3, summary.getTotalRatings());
        assertEquals(5, summary.getDistribution().size());
        assertEquals(5, summary.getDistribution().get(0).getStars());   // 5 stars listed first
        assertEquals(67, summary.getDistribution().get(0).getPercent());
        assertEquals(0, summary.getDistribution().get(1).getCount());   // nobody gave 4 stars
        assertEquals(33, summary.getDistribution().get(3).getPercent()); // 2 stars
        assertEquals("Roads", summary.getCategories().get(0).getName());
        assertEquals("4.5 / 5", summary.getCategories().get(0).getAverageDisplay());
    }

    @Test
    void withoutFeedbackEverythingIsEmpty() {
        when(feedbackRepository.averageRating()).thenReturn(null);
        when(feedbackRepository.count()).thenReturn(0L);
        when(feedbackRepository.countByRating()).thenReturn(List.of());
        when(feedbackRepository.averageByCategory()).thenReturn(List.of());

        SatisfactionSummary summary = new SatisfactionServiceImpl(feedbackRepository).summary();

        assertNull(summary.getAverage());
        assertEquals("-", summary.getAverageDisplay());
        assertEquals(0, summary.getRoundedStars());
        assertEquals(0, summary.getDistribution().get(0).getPercent());
        assertEquals(0, summary.getCategories().size());
    }
}
