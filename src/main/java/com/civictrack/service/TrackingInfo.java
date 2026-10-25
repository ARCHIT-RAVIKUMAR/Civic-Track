package com.civictrack.service;

import com.civictrack.enums.ComplaintStatus;

import java.util.List;

/** What anyone may see about a complaint when they look it up by reference number (nothing personal). */
public class TrackingInfo {

    public static class Step {
        private final ComplaintStatus status;
        private final String dateDisplay;

        public Step(ComplaintStatus status, String dateDisplay) {
            this.status = status;
            this.dateDisplay = dateDisplay;
        }

        public ComplaintStatus getStatus() {
            return status;
        }

        public String getDateDisplay() {
            return dateDisplay;
        }
    }

    private final String referenceNumber;
    private final String categoryName;
    private final ComplaintStatus status;
    private final String createdDisplay;
    private final String updatedDisplay;
    private final List<Step> steps;

    public TrackingInfo(String referenceNumber, String categoryName, ComplaintStatus status,
                        String createdDisplay, String updatedDisplay, List<Step> steps) {
        this.referenceNumber = referenceNumber;
        this.categoryName = categoryName;
        this.status = status;
        this.createdDisplay = createdDisplay;
        this.updatedDisplay = updatedDisplay;
        this.steps = steps;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public ComplaintStatus getStatus() {
        return status;
    }

    public String getCreatedDisplay() {
        return createdDisplay;
    }

    public String getUpdatedDisplay() {
        return updatedDisplay;
    }

    public List<Step> getSteps() {
        return steps;
    }
}
