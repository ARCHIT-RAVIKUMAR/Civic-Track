package com.civictrack.service;

import com.civictrack.entity.User;

import java.util.List;
import java.util.Locale;

/** Numbers about one field worker, shown on the worker performance and "my stats" pages. */
public class WorkerStats {

    /** What a citizen said about a job the worker completed. */
    public static class FeedbackNote {
        private final int rating;
        private final String comments;
        private final String complaintTitle;
        private final String referenceNumber;
        private final String dateDisplay;

        public FeedbackNote(int rating, String comments, String complaintTitle, String referenceNumber,
                            String dateDisplay) {
            this.rating = rating;
            this.comments = comments;
            this.complaintTitle = complaintTitle;
            this.referenceNumber = referenceNumber;
            this.dateDisplay = dateDisplay;
        }

        public int getRating() {
            return rating;
        }

        public String getComments() {
            return comments;
        }

        public String getComplaintTitle() {
            return complaintTitle;
        }

        public String getReferenceNumber() {
            return referenceNumber;
        }

        public String getDateDisplay() {
            return dateDisplay;
        }
    }

    private final User worker;
    private final long openJobs;
    private final long overdueJobs;
    private final long completedJobs;
    private final Double averageMinutes;
    private final Double averageRating;
    private final int ratingCount;
    private final List<FeedbackNote> recentFeedback;

    public WorkerStats(User worker, long openJobs, long overdueJobs, long completedJobs,
                       Double averageMinutes, Double averageRating, int ratingCount) {
        this(worker, openJobs, overdueJobs, completedJobs, averageMinutes, averageRating, ratingCount, List.of());
    }

    public WorkerStats(User worker, long openJobs, long overdueJobs, long completedJobs,
                       Double averageMinutes, Double averageRating, int ratingCount,
                       List<FeedbackNote> recentFeedback) {
        this.worker = worker;
        this.openJobs = openJobs;
        this.overdueJobs = overdueJobs;
        this.completedJobs = completedJobs;
        this.averageMinutes = averageMinutes;
        this.averageRating = averageRating;
        this.ratingCount = ratingCount;
        this.recentFeedback = recentFeedback;
    }

    public User getWorker() {
        return worker;
    }

    public long getOpenJobs() {
        return openJobs;
    }

    public long getOverdueJobs() {
        return overdueJobs;
    }

    public long getCompletedJobs() {
        return completedJobs;
    }

    public Double getAverageMinutes() {
        return averageMinutes;
    }

    public Double getAverageRating() {
        return averageRating;
    }

    public int getRatingCount() {
        return ratingCount;
    }

    public List<FeedbackNote> getRecentFeedback() {
        return recentFeedback;
    }

    /** "45 min", "5.5 h" or "2.3 days"; "-" when there is no data yet. */
    public String getAverageTimeDisplay() {
        if (averageMinutes == null) {
            return "-";
        }
        if (averageMinutes < 60) {
            return String.format(Locale.ROOT, "%.0f min", averageMinutes);
        }
        double hours = averageMinutes / 60.0;
        if (hours < 48) {
            return String.format(Locale.ROOT, "%.1f h", hours);
        }
        return String.format(Locale.ROOT, "%.1f days", hours / 24.0);
    }

    public String getAverageRatingDisplay() {
        return averageRating == null ? "-" : String.format(Locale.ROOT, "%.1f / 5", averageRating);
    }
}
