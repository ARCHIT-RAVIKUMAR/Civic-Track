package com.civictrack.service.impl;

import com.civictrack.repository.FeedbackRepository;
import com.civictrack.service.SatisfactionService;
import com.civictrack.service.SatisfactionSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class SatisfactionServiceImpl implements SatisfactionService {

    private final FeedbackRepository feedbackRepository;

    public SatisfactionServiceImpl(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    @Override
    public SatisfactionSummary summary() {
        Double average = feedbackRepository.averageRating();
        long total = feedbackRepository.count();

        Map<Integer, Long> counts = new HashMap<>();
        for (Object[] row : feedbackRepository.countByRating()) {
            counts.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        List<SatisfactionSummary.Row> distribution = new ArrayList<>();
        for (int stars = 5; stars >= 1; stars--) {
            long count = counts.getOrDefault(stars, 0L);
            int percent = total == 0 ? 0 : (int) Math.round(100.0 * count / total);
            distribution.add(new SatisfactionSummary.Row(stars, count, percent));
        }

        List<SatisfactionSummary.CategoryRating> categories = new ArrayList<>();
        for (Object[] row : feedbackRepository.averageByCategory()) {
            categories.add(new SatisfactionSummary.CategoryRating(
                    (String) row[0], ((Number) row[1]).doubleValue(), ((Number) row[2]).longValue()));
        }
        return new SatisfactionSummary(average, total, distribution, categories);
    }
}
