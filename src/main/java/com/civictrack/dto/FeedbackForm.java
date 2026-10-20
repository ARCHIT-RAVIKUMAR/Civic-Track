package com.civictrack.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class FeedbackForm {

    @NotNull(message = "Choose a rating")
    @Min(value = 1, message = "Rating must be 1 to 5")
    @Max(value = 5, message = "Rating must be 1 to 5")
    private Integer rating;

    @Size(max = 1000, message = "Comments are too long")
    private String comments;

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }
}
