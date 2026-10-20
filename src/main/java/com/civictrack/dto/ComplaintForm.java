package com.civictrack.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class ComplaintForm {

    @NotBlank(message = "Title is required")
    @Size(min = 5, max = 150, message = "Title must be 5 to 150 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 2000, message = "Description must be 10 to 2000 characters")
    private String description;

    @NotBlank(message = "Location is required")
    @Size(max = 255, message = "Location is too long")
    private String location;

    @NotNull(message = "Choose a category")
    private UUID categoryId;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }
}
