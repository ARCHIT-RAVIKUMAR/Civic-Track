package com.civictrack.service;

import java.util.List;
import java.util.Locale;

/** Overall and per-category citizen ratings, for the admin and authority pages. */
public class SatisfactionSummary {

    public static class Row {
        private final int stars;
        private final long count;
        private final int percent;

        public Row(int stars, long count, int percent) {
            this.stars = stars;
            this.count = count;
            this.percent = percent;
        }

        public int getStars() {
            return stars;
        }

        public long getCount() {
            return count;
        }

        public int getPercent() {
            return percent;
        }
    }

    public static class CategoryRating {
        private final String name;
        private final double average;
        private final long count;

        public CategoryRating(String name, double average, long count) {
            this.name = name;
            this.average = average;
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public double getAverage() {
            return average;
        }

        public long getCount() {
            return count;
        }

        public String getAverageDisplay() {
            return String.format(Locale.ROOT, "%.1f / 5", average);
        }
    }

    private final Double average;
    private final long totalRatings;
    private final List<Row> distribution;
    private final List<CategoryRating> categories;

    public SatisfactionSummary(Double average, long totalRatings, List<Row> distribution,
                               List<CategoryRating> categories) {
        this.average = average;
        this.totalRatings = totalRatings;
        this.distribution = distribution;
        this.categories = categories;
    }

    public Double getAverage() {
        return average;
    }

    public long getTotalRatings() {
        return totalRatings;
    }

    public List<Row> getDistribution() {
        return distribution;
    }

    public List<CategoryRating> getCategories() {
        return categories;
    }

    public String getAverageDisplay() {
        return average == null ? "-" : String.format(Locale.ROOT, "%.1f", average);
    }

    /** Number of filled stars (0-5) for the headline rating. */
    public int getRoundedStars() {
        return average == null ? 0 : (int) Math.round(average);
    }
}
