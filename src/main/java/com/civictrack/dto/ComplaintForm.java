package com.civictrack.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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

    @DecimalMin(value = "-90.0", message = "Latitude is out of range")
    @DecimalMax(value = "90.0", message = "Latitude is out of range")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude is out of range")
    @DecimalMax(value = "180.0", message = "Longitude is out of range")
    private Double longitude;

    @AssertTrue(message = "Map position is incomplete")
    public boolean isCoordinatesValid() {
        return (latitude == null) == (longitude == null);
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

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
